import syntaxtree.*;
import visitor.GJDepthFirst;
import java.io.FileWriter;
import java.io.IOException;  
import java.util.*;

public class Generator extends GJDepthFirst<String, String> { 
    DeclCollector dl;       //access to declarations and offsets
    String file_name;       //name of the file to be produced
    FileWriter myWriter;

    String current_class;   //used in recursion
    String current_method;  //used in recursion
    int reg_counter;        //used to produce unique register names
    int label_counter;      //used to produce unique label names
    
    Map<String, String> object_class; //<RegisterName, ClassName>
    Map<String, String> reg_array;  //<RegisterName, ArrayType>
    List<String> ExpressionList;    //this will hold the arguments of a function call 

    public Generator(DeclCollector dl, String fileName) throws Exception {
        current_class = null;
        current_method = null;
        this.dl = dl;
        object_class = new HashMap<String, String>();
        reg_array = new HashMap<String, String>();


        file_name = fileName;
        reg_counter = 0;
        label_counter = 0;
        // System.out.println("["+file_name+"]");

        myWriter = new FileWriter(file_name + "ll");

        //create v-tables for every class
        for (Map.Entry<String, Map<String, Map<String, String>>> entry: dl.class_methods.entrySet()) {
            String class_name = entry.getKey();
            Map<String, Map<String, String>> methods = entry.getValue();

            //if this class has only the main method skip this loop
            if (methods != null && methods.containsKey("MAIN")) {    
                myWriter.write("@." + class_name + "_vtable = global [0 x i8*] [ ]\n");
                continue;
            }

            int vtable_size = find_number_of_functions(class_name);
            myWriter.write("@." + class_name + "_vtable = global [" + vtable_size + " x i8*] [");

            //create an array of strings each string will correspond to one function of the v-table
            String[] vtable = new String[vtable_size];
            create_vtable(class_name, 0, vtable_size, vtable);  /// RECURSE
            //copy vtable (array of strings) to the file
            for(int i=0; i < vtable_size; i++) {
                myWriter.write(vtable[i]);
            }
            
            myWriter.write("]");
            myWriter.write("\n");
        }
        myWriter.write("\n\n");


        //files will certainly have these instructions
        String start = "declare i8* @calloc(i32, i32)\ndeclare i32 @printf(i8*, ...)\ndeclare void @exit(i32)\n"+
            "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n" +
            "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n" +
            "@_cNSZ = constant [15 x i8] c\"Negative size\\0a\\00\"\n\n" +
            "define void @print_int(i32 %i) {\n" +
            "\t%_str = bitcast [4 x i8]* @_cint to i8*\n" +
            "\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n" +
            "\tret void\n}\n\n" +
            "define void @throw_oob() {\n" +
            "\t%_str = bitcast [15 x i8]* @_cOOB to i8*\n" +
            "\tcall i32 (i8*, ...) @printf(i8* %_str)\n" +
            "\tcall void @exit(i32 1)\n" +
            "\tret void\n}\n\n" +
            "define void @throw_nsz() {\n" +
            "\t%_str = bitcast [15 x i8]* @_cNSZ to i8*\n" +
            "\tcall i32 (i8*, ...) @printf(i8* %_str)\n" +
            "\tcall void @exit(i32 1)\n" +
            "\tret void\n" +
            "}\n\n";
        myWriter.write(start);       

        //write my types of arrays
        start = "%int_arr = type {i32, i32*}\n" +
        "%bool_arr = type {i32, i1*}\n\n";
        myWriter.write(start);
    }

    public String convert_type(String type) {
        if(type.equals("INT")) {
            return "i32";
        } else if(type.equals("BOOLEAN")) {
            return "i1";
        } else if(type.equals("INT_ARRAY")) {
            return "%int_arr*";
        } else if(type.equals("BOOLEAN_ARRAY")) {
            return "%bool_arr*";
        } else {
            return "i8*";
        }
    }

    //finds the actual number of functions (overriden function count as 1) in the inheritance chain
    //this function is used in order to find the size of the v-table
    public int find_number_of_functions(String class_name) {
        int total = 0;
        if(dl.inherits_from.containsKey(class_name)) {  //if given class inherits from another class
            total = find_number_of_functions(dl.inherits_from.get(class_name));
        }
        
        Map<String, Integer> methods = dl.method_offset.get(class_name);
        if (methods != null) {  //if this class has any methods
            for (Map.Entry<String, Integer> e: methods.entrySet()) {
                if(!isFunctionOverriden(class_name, e.getKey())) {  //if this function is not overriden then count it
                    total++;
                }
            }
        }
        return total;      
    }

    //prerequisites: class of "class_name" must contain method of "method_name"
    //offset being null means this function is overriden and has the same offset as
    //the one in the superclass
    public boolean isFunctionOverriden(String class_name, String method_name) {
        if (dl.method_offset.get(class_name).get(method_name) == null) {
            return true;
        } else {
            return false;
        }
    }


    public int create_vtable(String class_name, int cnt, int limit, String[] vtable) throws Exception {
        String superclass;
        if (dl.inherits_from.containsKey(class_name)) {
            superclass = dl.inherits_from.get(class_name);
            cnt = create_vtable(superclass, cnt, limit, vtable);    //first we should write the functions of the superclass
        }

        Map<String, Map<String, String>> methods = dl.class_methods.get(class_name);

        if (methods == null) return cnt;

        String comma;
        if (cnt > 0 && cnt < limit) comma = ", ";
        else comma = "";

        //loop through every method 
        for (Map.Entry<String, Map<String, String>> entry1: methods.entrySet()) {
            String method_name = entry1.getKey();

            //if this function is overriden update array of strings
            if (isFunctionOverriden(class_name, method_name)) { 
                //update vtable with the function of the subclass
                int offset;
                offset = find_method_offset(class_name, method_name);
                vtable[offset/8] = vtable[offset/8].replaceAll("@\\b_*[a-zA-Z][_a-zA-Z0-9]*\\b", "@"+class_name);
                continue; //if this function is overriden skip loop
            }
            int index = cnt;
            vtable[index] = "i8* bitcast (" + convert_type(methods.get(method_name).get("RETURN")) + " (i8*";
            cnt++;

            //loop for every argument of this method
            Map<String, String> arguments = dl.class_method_args.get(class_name).get(method_name);
            if (arguments != null) {
                for(Map.Entry<String, String> entry2: arguments.entrySet()) {
                    vtable[index] = vtable[index] + ", ";
                    String type = convert_type(entry2.getValue());
                    vtable[index] = vtable[index] + type;
                }
            }

            if(cnt > 0) comma = ", ";
            if(cnt == limit) comma = "";
            vtable[index] = vtable[index] + ")* @" + class_name + "." + method_name + " to i8*)" + comma +"\n";
        }
        return cnt;
    }

    //find offset of a certain method in a virtual table
    public int find_method_offset(String class_name, String method_name) {
        Integer offset = -1;
        Map<String, Integer> methods = dl.method_offset.get(class_name);
        if(methods != null) {
            offset = methods.get(method_name);
            if(offset != null) return offset;
        }

        if (dl.inherits_from.containsKey(class_name)) {
            offset = find_method_offset(dl.inherits_from.get(class_name), method_name);
        }
        return offset;
    }

    //find offset of a certain field in an object
    public int find_field_offset(String class_name, String field) {
        Integer offset = -1;
        Map<String, Integer> fields = dl.variable_offset.get(class_name);
        if(fields != null) {
            offset = fields.get(field);
            if(offset != null) return offset;
        }

        if (dl.inherits_from.containsKey(class_name)) {
            offset = find_field_offset(dl.inherits_from.get(class_name), field);
        }
        return offset;
    }

    /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> "public"
    * f4 -> "static"
    * f5 -> "void"
    * f6 -> "main"
    * f7 -> "("
    * f8 -> "String"
    * f9 -> "["
    * f10 -> "]"
    * f11 -> Identifier()
    * f12 -> ")"
    * f13 -> "{"
    * f14 -> ( VarDeclaration() )*
    * f15 -> ( Statement() )*
    * f16 -> "}"
    * f17 -> "}"
    */
    @Override
    public String visit(MainClass mc, String argu) throws Exception {
        myWriter.write("define i32 @main() {\n");
        current_class = mc.f1.accept(this,null);
        current_method = "MAIN";
        String args = mc.f11.accept(this,null);

        if(mc.f14.present()) {  //if there are any variables declared in the main function
            //allocate memory for the local variables of main function
            //theses variables are contained in map class_methods of DeclCollector
            Map <String, String> method_variables = dl.class_methods.get(current_class).get("MAIN");
            
            if (method_variables != null) {
                //loop through every variable of this function
                for(Map.Entry<String, String> e: method_variables.entrySet()) {
                    String VarName = e.getKey();

                    if(VarName.equals(args)) continue;  //special case -> ignore

                    String type = e.getValue();
                    myWriter.write("\t%" + VarName + " = alloca " + convert_type(type) +"\n");
                }
            }
        }
        myWriter.write("\n");

        if(mc.f15.present()) {
            mc.f15.accept(this, null);
        }
        myWriter.write("\n\tret i32 0\n}\n\n");
        return null;
    }

     /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> ( VarDeclaration() )*
    * f4 -> ( MethodDeclaration() )*
    * f5 -> "}"
    */
    @Override
    public String visit(ClassDeclaration cd, String argu) throws Exception{
        current_class = cd.f1.accept(this, null);
        if(cd.f4.present()) {
            cd.f4.accept(this, null);
        }
        return null;
    }

    /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "extends"
    * f3 -> Identifier()
    * f4 -> "{"
    * f5 -> ( VarDeclaration() )*
    * f6 -> ( MethodDeclaration() )*
    * f7 -> "}"
    */
    @Override
    public String visit(ClassExtendsDeclaration ced, String argu) throws Exception {
        current_class = ced.f1.accept(this, null);
        if(ced.f6.present()) {
            ced.f6.accept(this, null);
        }
        return null;
    }

    /**
    * f0 -> "public"
    * f1 -> Type()
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( FormalParameterList() )?
    * f5 -> ")"
    * f6 -> "{"
    * f7 -> ( VarDeclaration() )*
    * f8 -> ( Statement() )*
    * f9 -> "return"
    * f10 -> Expression()
    * f11 -> ";"
    * f12 -> "}"
    */
    @Override
    public String visit(MethodDeclaration md, String argu) throws Exception {
        String method_name = md.f2.accept(this, null);
        current_method = method_name;
        String return_type = convert_type(md.f1.accept(this, null));
        String method_definition ="define " + return_type + " @" + current_class + "." + method_name;
        method_definition = method_definition + "(i8* %this";


        //loop for every argument of this method
        Map<String, String> arguments = get_function_args(current_class, method_name);  //get map of arguments
        if (arguments != null) {
            for(Map.Entry<String, String> entry2: arguments.entrySet()) {
                String type = convert_type(entry2.getValue());
                String VarName = entry2.getKey();
                method_definition = method_definition + ", "+ type + " %." + VarName;
            }
        }

        method_definition = method_definition + ") {\n";
        myWriter.write(method_definition);

        //allocate memory for the local variables of this function (if any)
        //theses variables are contained in map class_methods of DeclCollector
        Map <String, String> method_variables = dl.class_methods.get(current_class).get(method_name);
        if (method_variables != null) {
            //loop through every variable of this function
            for(Map.Entry<String, String> e: method_variables.entrySet()) {
                String VarName = e.getKey();

                if(VarName.equals("RETURN")) continue;  //special case -> ignore

                String type = e.getValue();
                myWriter.write("\t%" + VarName + " = alloca " + convert_type(type) +"\n");

                //is this variable an argument?? If so then store value in memory
                if(arguments != null) {
                    if(arguments.containsKey(VarName)) {
                        myWriter.write("\tstore "+ convert_type(type) + " %." + VarName + ", "+ convert_type(type) + "* %" +VarName +"\n");
                    }
                }
            }
        }
        myWriter.write("\n");

        if (md.f8.present()) {  //if this method has any statements
            md.f8.accept(this, null);
        }

        String return_reg = md.f10.accept(this, null);  ////////////////////////// EXPRESSION
        myWriter.write("\n\tret "+ return_type + " "+ convert_to_register(return_reg) +"\n");/////////////////////// => f10
        myWriter.write("}\n\n");
        return null;
    }

    /* f0 -> <INTEGER_LITERAL> */
    @Override
    public String visit(IntegerLiteral il, String argu) throws Exception {
        return il.f0.toString();
    }

    /* f0 -> <IDENTIFIER> */
    @Override 
    public String visit(Identifier i, String argu) {
        String s = i.f0.toString();
        return s;
    }

    @Override
    public String visit(TrueLiteral tl, String argu) {
        return tl.f0.toString();
    }

    @Override
    public String visit(FalseLiteral tl, String argu) {
        return tl.f0.toString();
    }

    /* f0 -> "int" */
    @Override
    public String visit(IntegerType it, String argu) throws Exception{
        return "INT";
    }

    /* f0 -> "boolean" */
    @Override
    public String visit(BooleanType bt, String argu) throws Exception{
        return "BOOLEAN";
    }

    /**
    * f0 -> BooleanArrayType()
    *       | IntegerArrayType()
    */
    @Override
    public String visit(ArrayType at, String argu) throws Exception{
        String type = at.f0.accept(this, null);
        return type;
    }

    /**
    * f0 -> "boolean"
    * f1 -> "["
    * f2 -> "]"
    */
    @Override
    public String visit(BooleanArrayType bt, String argu) throws Exception{
        return "BOOLEAN_ARRAY";
    }

    /**
    * f0 -> "int"
    * f1 -> "["
    * f2 -> "]"
    */
    @Override
    public String visit(IntegerArrayType it, String argu) throws Exception{
        return "INT_ARRAY";
    }
    
    /* f0 -> "this" */
    @Override
    public String visit(ThisExpression ts, String argu) throws Exception{
        return "THIS";
    }

    /**
    * f0 -> "new"
    * f1 -> Identifier()
    * f2 -> "("
    * f3 -> ")"
    */
    @Override
    public String visit(AllocationExpression ae, String argu) throws Exception {
        String register1 = "%r" + Integer.toString(reg_counter++);
        String line = "\t"+register1 + " = call i8* @calloc(i32 1, i32 ";
        String class_name = ae.f1.accept(this, null);

        //find size of the object to be created
        int object_size = get_object_size(class_name) + 8;   //plus pointer to vtable 
        line = line + Integer.toString(object_size) + ")\n";
        myWriter.write(line);

        String register2 ="%r" + Integer.toString(reg_counter++);
        String register3 = "%r" + Integer.toString(reg_counter++);
        
        myWriter.write("\t"+register2 + " = bitcast i8* " + register1 + " to i8***\n");
        int vtable_size = find_number_of_functions(class_name);
        myWriter.write("\t"+register3 + " = getelementptr [" + vtable_size +" x i8*], ["+ vtable_size +" x i8*]* @."+ class_name +"_vtable, i32 0, i32 0\n");
        myWriter.write("\tstore i8** "+ register3 +", i8*** " + register2 +"\n");
        
        object_class.put(register1, class_name);    //insert register holding the object into map for future reference
        return register1;
    }

    //finds the size of the object in memory
    public int get_object_size(String class_name) {
        int size = 0;   

        Map<String, String> variables = dl.class_fields.get(class_name);
        if(variables != null) {
            for(Map.Entry<String, String> e: variables.entrySet()) {
                String type = e.getValue();
                if(type.equals("INT")) size += 4;
                else if(type.equals("BOOLEAN")) size +=1;
                else size+=8;    //array or pointer to another object
            }
        }
        //if this class inherits from another class
        if(dl.inherits_from.containsKey(class_name)) {
            size += get_object_size(dl.inherits_from.get(class_name));  //get size of its object
        }

        return size;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( ExpressionList() )?
    * f5 -> ")"
    */
    @Override
    public String visit(MessageSend ms, String argu) throws Exception {
        String primary_expression = ms.f0.accept(this, null);
        String reg_object = convert_to_register(primary_expression);   //get register that points to the object
        String function = ms.f2.accept(this, null);
        
        //if this function is called on this object
        if(reg_object.equals("THIS")) reg_object = "%this";

        String reg1 = "%r" + Integer.toString(reg_counter++);
        String reg2 = "%r" + Integer.toString(reg_counter++);
        String reg3 = "%r" + Integer.toString(reg_counter++);
        String reg4 = "%r" + Integer.toString(reg_counter++);
        String reg5 = "%r" + Integer.toString(reg_counter++);
        String reg6 = "%r" + Integer.toString(reg_counter++);

        //get address of function from vtable
        myWriter.write("\t" + reg1 + " = bitcast i8* " + reg_object + " to i8***\n");
        myWriter.write("\t"+ reg2 + " = load i8**, i8*** " + reg1 + "\n");

        //find on what type of class this method is called on
        String class_name = null;
        if(primary_expression.charAt(0) != '%' && !primary_expression.equals("THIS")) {   //if primary expression is not a register
            
            if(isLocalVar(primary_expression)) {    //check if Primary_expression is a local variable
                class_name = dl.class_methods.get(current_class).get(current_method).get(primary_expression);
            } else if(isField(primary_expression, current_class)) { //check if Primary_expression is a field of a class
                class_name = find_type(current_class, primary_expression);
            }
        } else if(primary_expression.equals("THIS")) {
            class_name = current_class;
        }  else {   //primary expression is a returned register
            class_name = object_class.get(primary_expression);
        }
        
        //find offset for access in vtable
        int offset = find_method_offset(class_name, function) / 8;
        myWriter.write("\t"+ reg3 + " = getelementptr i8*, i8** " + reg2 + ", i32 "+ offset +"\n");
        myWriter.write("\t"+ reg4 + " = load i8*, i8** " + reg3+ "\n");

        //find and write return type of function
        String return_type = get_return_type(class_name, function);
        String function_type = return_type;
        return_type = convert_type(return_type);
        myWriter.write("\t"+ reg5 + " = bitcast i8* " + reg4 + " to "+ return_type + " (i8*");  //RETURN(AR1, ...)
        
        //find and write type of arguments
        Map<String, String> function_args = get_function_args(class_name, function);
        String argument_types = "";
        if (function_args != null) {            
            for(Map.Entry<String, String> e: function_args.entrySet()) {
                argument_types = argument_types + ", " + convert_type(e.getValue());
            }
        }
        myWriter.write(argument_types + ")*\n");

        ExpressionList = new ArrayList<String>();  
        
        if(ms.f4.present()) {
            ms.f4.accept(this, null);   //EXPRESSION LIST => fill ExpressionList with arguments
        }
        myWriter.write("\t"+ reg6 + " = call " + return_type + " " + reg5 + "(i8* " + reg_object);
        //write values of arguments
        if (function_args != null) {    
            for(Map.Entry<String, String> e: function_args.entrySet()) {
                String value = ExpressionList.get(0);   //index is always zero because every time we get a value we also remove it from the list
                ExpressionList.remove(0);   //remove this argument from expression list, we don't need it anymore
                myWriter.write(", " + convert_type(e.getValue()) + " "+value);  //////////////////////////
            }
        }
        myWriter.write(")\n");

        //if this function returns an object then keep the name of the register and its type in the map 
        //for future reference
        if(!function_type.equals("INT") && !function_type.equals("BOOLEAN") && !function_type.equals("BOOLEAN_ARRAY") && !function_type.equals("INT_ARRAY")) {
            object_class.put(reg6, function_type);
        }

        //if this function returns an array then keep the name of the register and its type in map reg_array
        //in case this register comes up if other expressions
        if(function_type.equals("INT_ARRAY")) {
            reg_array.put(reg6, "%int_arr");
        }
        if(function_type.equals("BOOLEAN_ARRAY")) {
            reg_array.put(reg6, "%bool_arr");
        }

        return reg6;
    }

    //searches for function in the inheritance chain
    //then returns the map with the function arguments
    public Map<String, String> get_function_args(String class_name, String function) {
        boolean flag = false;
        Map<String, String> function_args = null;
        
        Map<String, Map<String, String>> methods = dl.class_method_args.get(class_name);
        if(methods != null) {
            function_args = methods.get(function);
            if (methods.containsKey(function)) flag = true;
        }

        //if the function was not found search it in a superclass
        //the input programs are semantically correct so there is no way the function doesn't exist
        //in the inheritance chain
        if(flag == false) function_args = get_function_args(dl.inherits_from.get(class_name), function);
        return function_args;
    }

    //searches for a function in the inheritance chain and returns its return type
    public String get_return_type(String class_name, String function) {
        String return_type = null;
        Map<String, Map<String, String>> methods = dl.class_methods.get(class_name);
        
        if (methods != null) {
            Map<String, String> variables = methods.get(function);
            
            if(variables != null) {
                return_type = variables.get("RETURN");
            }
        }

        //if the function was not found search it in a superclass
        //the input programs are semantically correct so there is no way the function doesn't exist
        //in the inheritance chain
        if(return_type == null && dl.inherits_from.containsKey(class_name))  return_type =  get_return_type(dl.inherits_from.get(class_name), function);
        return return_type;
    }

    /**
    * f0 -> Expression()
    * f1 -> ExpressionTail()
    */
    @Override
    public String visit(ExpressionList el, String argu) throws Exception {
        String register = el.f0.accept(this, null);
        register = convert_to_register(register);
        ExpressionList.add(register);
        el.f1.accept(this, null);
        return null;////////////////////////
    }

    /**
    * f0 -> ","
    * f1 -> Expression()
    */
    @Override
    public String visit(ExpressionTerm et, String argu) throws Exception {
        String register = et.f1.accept(this, null);
        register = convert_to_register(register);
        ExpressionList.add(register);
        return null;
    }
    
    /**
    * f0 -> "System.out.println"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> ";"
    */
    @Override
    public String visit(PrintStatement ps, String argu) throws Exception {
        String register = ps.f2.accept(this,null);
        myWriter.write("\tcall void (i32) @print_int(i32 " + convert_to_register(register) +")\n");
        return null;
    }

    //checks if a string is integer literal
    public boolean isNumber(String s) {
        boolean flag = true; 
        try {
            int Value = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            flag = false;
        }
        return flag;
    }

    //for when we want to read a certain value 
    //this function loads to a register
    public String convert_to_register(String register) throws Exception {
        if(register.charAt(0) != '%') { //if this is neither a register nor a number then it must be a known variable 
            //is it a local variable of a method?
            if(isLocalVar(register)) {
                String r = "%r" + Integer.toString(reg_counter++);
                String type = dl.class_methods.get(current_class).get(current_method).get(register);
                
                type = convert_type(type);
                myWriter.write("\t" + r+ " = load " + type + ", " + type + "* %" + register + "\n");
                return r;
            }

            //is it a field of a class??
            else if(isField(register, current_class)) {
                //get offset of this field
                int offset = find_field_offset(current_class, register) + 8;

                //find type of fields
                String field_type = find_type(current_class, register);
                field_type = convert_type(field_type);

                String reg0 = "%r" + Integer.toString(reg_counter++);
                String reg1 = "%r" + Integer.toString(reg_counter++);
                String reg2 = "%r" + Integer.toString(reg_counter++);
                myWriter.write("\t"+ reg0 + " = getelementptr i8, i8* %this, i32 " + Integer.toString(offset) + "\n");
                myWriter.write("\t" + reg1 + " = bitcast i8* " + reg0 + " to " + field_type + "*\n");
                myWriter.write("\t"+ reg2+" = load " + field_type + ", " + field_type + "* " + reg1 + "\n");
                return reg2;
            }
            if(register.equals("THIS")) return "%this";
        }
        return register;
    }

    //returns the type of a class field
    public String find_type(String class_name, String iden) {
        String result = null;
        Map<String, String> class_fields = dl.class_fields.get(class_name);
        if(class_fields != null && class_fields.containsKey(iden))  {
            return class_fields.get(iden);
        }
        else {
            if (dl.inherits_from.containsKey(class_name)) { //search it in the inheritance chain
                String superClass = dl.inherits_from.get(class_name);
                result = find_type(superClass, iden);
            }
        } 
        return result;
    }

    //checks if a string is a local variable of current_method
    public boolean isLocalVar(String iden) {
        Map<String, Map<String, String>> methods = dl.class_methods.get(current_class);
        if (methods == null)    return false;
        Map<String, String> method_variables = methods.get(current_method);
        if (method_variables == null)   return false;
        if(method_variables.containsKey(iden)) return true;
        else return false;
    }

    //checks if a string is a field of current_class (or its superclasses)
    public boolean isField(String iden, String class_name) {
        boolean flag = false;
        Map<String, String> class_fields = dl.class_fields.get(class_name);
        // if(class_fields == null)    return false;
        if(class_fields != null && class_fields.containsKey(iden))  return true;
        else {
            if (dl.inherits_from.containsKey(class_name)) { //search it in the inheritance chain
                String superClass = dl.inherits_from.get(class_name);
                flag = isField(iden, superClass);
            }
        } 
        
        return flag;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "*"
    * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(TimesExpression te, String argu) throws Exception {
        String reg1 = te.f0.accept(this, null);
        String reg2 = te.f2.accept(this, null);
        String reg3 = "%r" + Integer.toString(reg_counter++);
        myWriter.write("\t" + reg3 + " = mul i32 " + convert_to_register(reg1) + ", " + convert_to_register(reg2) + "\n");
        return reg3;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "+"
    * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(PlusExpression te, String argu) throws Exception {
        String reg1 = te.f0.accept(this, null);
        String reg2 = te.f2.accept(this, null);
        String reg3 = "%r" + Integer.toString(reg_counter++);
        myWriter.write("\t" + reg3 + " = add i32 " + convert_to_register(reg1) + ", " + convert_to_register(reg2) + "\n");
        return reg3;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "-"
    * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(MinusExpression te, String argu) throws Exception {
        String reg1 = te.f0.accept(this, null);
        String reg2 = te.f2.accept(this, null);
        String reg3 = "%r" + Integer.toString(reg_counter++);
        myWriter.write("\t" + reg3 + " = sub i32 " + convert_to_register(reg1) + ", " + convert_to_register(reg2) + "\n");
        return reg3;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "<"
    * f2 -> PrimaryExpression()
    */
    @Override
    public String visit(CompareExpression te, String argu) throws Exception {
        String reg1 = convert_to_register(te.f0.accept(this, null));
        String reg2 = convert_to_register(te.f2.accept(this, null));
        String reg = "%r" + Integer.toString(reg_counter++);
        myWriter.write("\t" + reg + " = icmp slt i32 " + reg1 + ", " + reg2 + "\n");
        return reg;
    }
    
    /**
    * f0 -> Clause()
    * f1 -> "&&"
    * f2 -> Clause()
    */
    @Override
    public String visit(AndExpression ae, String argu) throws Exception {
        String c1 = ae.f0.accept(this, null);
        c1 = convert_to_register(c1);

        String left_clause_true = "label" + Integer.toString(label_counter++);
        String true_result = "label" + Integer.toString(label_counter++);
        String false_result = "label" + Integer.toString(label_counter++);
        String End = "label" + Integer.toString(label_counter++);

        String RESULT1 = "%r" + Integer.toString(reg_counter++);
        String RESULT2 = "%r" + Integer.toString(reg_counter++);
        String final_result = "%r" + Integer.toString(reg_counter++);
        String TEMP = "%r" + Integer.toString(reg_counter++);

        myWriter.write("\t"+TEMP+" = xor i1 1, "+c1+"\n");  //get opposite value (if c1==false then temp=true)
        myWriter.write("\tbr i1 "+TEMP+", label %"+false_result+", label %"+left_clause_true+"\n"); //short-circuit, jump directly to label false_result
        myWriter.write(left_clause_true+":\n"); //this means that c1 was true
        String c2 = convert_to_register(ae.f2.accept(this, null));  //now check c2
        myWriter.write("\tbr i1 "+c2+", label %"+true_result+", label %"+false_result+"\n");
        
        //result of AndExpression is True
        myWriter.write(true_result+":\n");
        myWriter.write("\t"+ RESULT1+" = and i1 true, true\n");
        myWriter.write("\tbr label%"+End+"\n");

        //result of AndExpression is False
        myWriter.write(false_result+":\n");
        myWriter.write("\t"+ RESULT2+" = and i1 true, false\n");
        myWriter.write("\tbr label%"+End+"\n");
        myWriter.write(End+":\n");
        myWriter.write("\t"+final_result+" = phi i1 ["+RESULT1+", %"+true_result+"], ["+RESULT2+", %"+false_result+"]\n");

        /////////////////////////////////////////////////////////////
        return final_result;/////////
    }

    /**
    * f0 -> "!"
    * f1 -> Clause()
    */
    @Override
    public String visit(NotExpression ne, String argu) throws Exception {
        String c = ne.f1.accept(this,null);
        c = convert_to_register(c);
        String reg = "%r" + Integer.toString(reg_counter++);
        myWriter.write("\t"+reg+" = xor i1 1, "+c+"\n");
        return reg;
    }

    /**
    * f0 -> "("
    * f1 -> Expression()
    * f2 -> ")"
    */
    @Override
    public String visit(BracketExpression be, String argu) throws Exception {
        String r = be.f1.accept(this, null);
        return r;
    }

    /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
    @Override
    public String visit(AssignmentStatement as, String argu) throws Exception {     
        String iden = as.f0.accept(this, null);
        String expr = as.f2.accept(this, iden);  

        String reg = convert_to_register(expr);  
        String reg0 = "%r" + Integer.toString(reg_counter++);
        String reg1 = "%r" + Integer.toString(reg_counter++);
        String type;
        String type1 = null;

        if(isLocalVar(iden)) {  //first search locally
            type1 = type = dl.class_methods.get(current_class).get(current_method).get(iden);
            type = convert_type(type);
            myWriter.write("\tstore " + type + " "+ reg +", " + type +"* %" + iden + "\n");
        }
        else if (isField(iden, current_class)) {
            //get offset of field
            int offset = find_field_offset(current_class, iden) + 8;
            //get type of field
            type1 = type = find_type(current_class, iden);
            type = convert_type(type);
            myWriter.write("\t" + reg0 + " = getelementptr i8, i8* %this, i32 " + Integer.toString(offset) + "\n");
            myWriter.write("\t" + reg1 + " = bitcast i8* " + reg0 + " to " + type +"*\n");
            myWriter.write("\tstore " + type + " " + reg +", " + type + "* " + reg1 + "\n");
        }
        
        //if this function returns an object then keep the name of the register and its type in the map 
        //for future reference
        if(!type1.equals("INT") && !type1.equals("BOOLEAN") && !type1.equals("BOOLEAN_ARRAY") && !type1.equals("INT_ARRAY")) {
            object_class.put(reg1, type1);
        }
        if(reg_array.containsKey(expr)) {   //if Expression(f2) returned a register that exists in map reg_array (meaning that this register is of type array)
            String arr_type = reg_array.get(expr);
            reg_array.put(reg1, arr_type);  //insert new register holding the array in map for future reference
        }
        
        return reg1;
    }

    /**
    * f0 -> Identifier()
    * f1 -> "["
    * f2 -> Expression()
    * f3 -> "]"
    * f4 -> "="
    * f5 -> Expression()
    * f6 -> ";"
    */
    @Override
    public String visit(ArrayAssignmentStatement aas, String argu) throws Exception{
        String arr = aas.f0.accept(this,null);
        String type_of_array;
        //find type of array
        if(isLocalVar(arr)) {
            type_of_array = dl.class_methods.get(current_class).get(current_method).get(arr);
            type_of_array = convert_type(type_of_array); 
        } else if(isField(arr, current_class)){    //if it's not a local variable of current method then it is a field of class
                    //since we assume that the input programs are semantically correct
            type_of_array = find_type(current_class, arr);
            type_of_array = convert_type(type_of_array); 
        } else {
            type_of_array = reg_array.get(arr); //f0 must have returned the register holding the array
        }

        arr = convert_to_register(arr); //<= of type %int_arr* / %bool_arr*

        String struct, type;
        if(type_of_array.charAt(1) == 'i') {    //it's an integer array
            struct = "%int_arr";
            type = "i32";
        } else {    //it's a boolean array
            struct = "%bool_arr";
            type = "i1";
        }

        String index = aas.f2.accept(this, null);
        index = convert_to_register(index);
        String expr = aas.f5.accept(this, null);
        String r1 = "%r" + Integer.toString(reg_counter++);
        String size = "%r" + Integer.toString(reg_counter++);
        String r3 = "%r" + Integer.toString(reg_counter++);
        String r4 = "%r" + Integer.toString(reg_counter++);
        String r5 = "%r" + Integer.toString(reg_counter++);
        String r6 = "%r" + Integer.toString(reg_counter++);
        String r7 = "%r" + Integer.toString(reg_counter++);
        String r8 = "%r" + Integer.toString(reg_counter++);
        String r9 = "%r" + Integer.toString(reg_counter++);
        String l1 = "label" + Integer.toString(label_counter++);
        String l2 = "label" + Integer.toString(label_counter++);

        //load size of the array
        myWriter.write("\t"+r1+" = getelementptr "+struct+", "+struct+"* "+arr+", i32 0, i32 0\n");
        myWriter.write("\t"+size+" = load i32, i32* "+r1+"\n");

        //check if index is within bounds
        myWriter.write("\t"+r3+" = icmp sge i32 " + index +", 0\n");    //check if index is greater than zero
        myWriter.write("\t"+r4+" = icmp slt i32 "+ index +", " + size+"\n"); //check if index is less than the size of the array
        myWriter.write("\t" + r5+ " = and i1 " +r3 +", " +r4+"\n");
        myWriter.write("\tbr i1 " + r5 + ", label %"+l1+ ", label %"+l2+"\n");
        myWriter.write(l2+":\n");
        myWriter.write("\tcall void @throw_oob()\n");
        myWriter.write("\tbr label %" + l1+"\n");

        //get address of element
        myWriter.write(l1+":\n");
        myWriter.write("\t"+r7+" = getelementptr "+struct+", "+struct+"* "+arr+", i32 0, i32 1\n"); //get address of array
        myWriter.write("\t"+r9+" = load "+type+"*, "+type+"** "+r7+"\n");
        myWriter.write("\t"+r8+" = getelementptr "+type+", "+type+"* "+r9+", i32 "+index+"\n");  //get address of element
        myWriter.write("\tstore "+type+" "+convert_to_register(expr)+", "+type+"* "+r8+"\n");    //store the value to that address

        return null;//////////////
    }

    /**
    * f0 -> "new"
    * f1 -> "boolean"
    * f2 -> "["
    * f3 -> Expression()
    * f4 -> "]"
    */
    @Override
    public String visit(BooleanArrayAllocationExpression baae, String argu) throws Exception {
        String size = convert_to_register(baae.f3.accept(this, null));
        
        //write instructions thath check if the size of the array is negative
        String r1 = "%r" + Integer.toString(reg_counter++);
        String r2 = "%r" + Integer.toString(reg_counter++);
        String r3 = "%r" + Integer.toString(reg_counter++);
        String r4 = "%r" + Integer.toString(reg_counter++);
        String r5 = "%r" + Integer.toString(reg_counter++);
        String r6 = "%r" + Integer.toString(reg_counter++);
        String r7 = "%r" + Integer.toString(reg_counter++);

        String l1 = "label" + Integer.toString(label_counter++);
        String l2 = "label" + Integer.toString(label_counter++);

        myWriter.write("\t" + r1 + " = icmp sge i32 " + size + ", 0\n"); //is size >= 0??
        myWriter.write("\tbr i1 "+ r1 +", label %"+ l1+", label %"+l2+"\n");
        myWriter.write(l2 + ":\n");
        myWriter.write("\tcall void @throw_nsz()\n");
        myWriter.write("\tbr label %" + l1 + "\n");
        myWriter.write(l1 + ":\n");

        //allocate memory for %int_arr = type {i32, i32*}
        myWriter.write("\t"+r2+" = call i8* @calloc(i32 1, i32 12)\n"); //12 = size of int(4) + size of pointer(8)
        myWriter.write("\t"+r3+" = bitcast i8* "+r2+" to %bool_arr*\n");
        myWriter.write("\t"+r4+" = getelementptr %bool_arr, %bool_arr* "+r3+", i32 0, i32 0\n"); //get size of struct in order to store the size of the array
        myWriter.write("\tstore i32 " + size+ ", i32* "+r4+"\n");

        //get pointer of the actual array
        myWriter.write("\t"+r5+" = getelementptr %bool_arr, %bool_arr* " + r3 + ", i32 0, i32 1\n");

        //allocate memory for the actual array
        myWriter.write("\t"+r6+" = call i8* @calloc(i32 "+size+",i32 1)\n"); //each element is 4 bytes
        myWriter.write("\t"+r7+" = bitcast i8* "+r6+" to i1*\n");
        myWriter.write("\tstore i1* " +r7+", i1** "+r5+"\n");

        reg_array.put(r3, "%bool_arr"); //for future reference

        return r3;
    }

    /**
    * f0 -> "new"
    * f1 -> "int"
    * f2 -> "["
    * f3 -> Expression()
    * f4 -> "]"
    */
    @Override
    public String visit(IntegerArrayAllocationExpression iaae, String argu) throws Exception {
        String size = convert_to_register(iaae.f3.accept(this, null));
        
        //write instructions thath check if the size of the array is negative
        String r1 = "%r" + Integer.toString(reg_counter++);
        String r2 = "%r" + Integer.toString(reg_counter++);
        String r3 = "%r" + Integer.toString(reg_counter++);
        String r4 = "%r" + Integer.toString(reg_counter++);
        String r5 = "%r" + Integer.toString(reg_counter++);
        String r6 = "%r" + Integer.toString(reg_counter++);
        String r7 = "%r" + Integer.toString(reg_counter++);

        String l1 = "label" + Integer.toString(label_counter++);
        String l2 = "label" + Integer.toString(label_counter++);

        myWriter.write("\t" + r1 + " = icmp sge i32 " + size + ", 0\n"); //is size >= 0??
        myWriter.write("\tbr i1 "+ r1 +", label %"+ l1+", label %"+l2+"\n");
        myWriter.write(l2 + ":\n");
        myWriter.write("\tcall void @throw_nsz()\n");
        myWriter.write("\tbr label %" + l1 + "\n");
        myWriter.write(l1 + ":\n");

        //allocate memory for %int_arr = type {i32, i32*}
        myWriter.write("\t"+r2+" = call i8* @calloc(i32 1, i32 12)\n"); //12 = size of int(4) + size of pointer(8)
        myWriter.write("\t"+r3+" = bitcast i8* "+r2+" to %int_arr*\n");
        myWriter.write("\t"+r4+" = getelementptr %int_arr, %int_arr* "+r3+", i32 0, i32 0\n"); //get size of struct in order to store the size of the array
        myWriter.write("\tstore i32 " + size+ ", i32* "+r4+"\n");

        //get pointer of the actual array
        myWriter.write("\t"+r5+" = getelementptr %int_arr, %int_arr* " + r3 + ", i32 0, i32 1\n");

        //allocate memory for the actual array
        myWriter.write("\t"+r6+" = call i8* @calloc(i32 "+size+",i32 4)\n"); //each element is 4 bytes
        myWriter.write("\t"+r7+" = bitcast i8* "+r6+" to i32*\n");
        myWriter.write("\tstore i32* " +r7+", i32** "+r5+"\n");

        reg_array.put(r3, "%int_arr");  //for future reference

        return r3;
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> "length"
    */
    @Override
    public String visit(ArrayLength al, String argu) throws Exception {
        String arr = al.f0.accept(this, null);
        String type_of_array;

        //find type of array
        if(isLocalVar(arr)) {
            type_of_array = dl.class_methods.get(current_class).get(current_method).get(arr);
            type_of_array = convert_type(type_of_array); 
        } else if(isField(arr, current_class)){    //if it's not a local variable of current method then it is a field of class
                    //since we assume that the input programs are semantically correct
            type_of_array = find_type(current_class, arr);
            type_of_array = convert_type(type_of_array); 
        } else {
            type_of_array = reg_array.get(arr); //PrimaryExpression(f0) must have returned the register holding the array
        }

        //get register
        arr = convert_to_register(arr); //will get sth of type %int_arr* or %bool_arr*

        String struct;
        if(type_of_array.charAt(1) == 'i') {    //it's an integer array
            struct = "%int_arr";
        } else {    //it's a boolean array
            struct = "%bool_arr";
        }

        String r = "%r" + Integer.toString(reg_counter++);
        String r1 = "%r" + Integer.toString(reg_counter++);
        //get size of array
        myWriter.write("\t"+r+" = getelementptr "+struct+", "+struct+"* "+arr+", i32 0, i32 0\n");
        myWriter.write("\t"+r1+" = load i32, i32* "+r+"\n");

        return r1;  //return register holding the size of the array
    }

    /**
    * f0 -> PrimaryExpression()
    * f1 -> "["
    * f2 -> PrimaryExpression()
    * f3 -> "]"
    */
    @Override
    public String visit(ArrayLookup al, String argu) throws Exception {
        String array = al.f0.accept(this,null);
        String index = convert_to_register(al.f2.accept(this, null));
        String type_of_array=null;
        //find type of array
        if(isLocalVar(array)) {
            type_of_array = dl.class_methods.get(current_class).get(current_method).get(array);
            type_of_array = convert_type(type_of_array);
        } else if(isField(array, current_class)){    //if it's not a local variable of current method check it is a field of class
            type_of_array = find_type(current_class, array);
            type_of_array = convert_type(type_of_array);
        } else {
            type_of_array = reg_array.get(array);   //PrimaryExpression(f0) must have returned the register holding the array
        }
        array = convert_to_register(array); //<= of type %int_arr* / %bool_arr*

        String struct, type;
        if(type_of_array.charAt(1) == 'i') {    //it's an integer array
            struct = "%int_arr";
            type = "i32";
        } else {    //it's a boolean array
            struct = "%bool_arr";
            type = "i1";
        }

        String size = "%r" + Integer.toString(reg_counter++);
        String r1 = "%r" + Integer.toString(reg_counter++);
        String r3 = "%r" + Integer.toString(reg_counter++);
        String r4 = "%r" + Integer.toString(reg_counter++);
        String r5 = "%r" + Integer.toString(reg_counter++);
        String r6 = "%r" + Integer.toString(reg_counter++);
        String r7 = "%r" + Integer.toString(reg_counter++);
        String r8 = "%r" + Integer.toString(reg_counter++);
        String r2 = "%r" + Integer.toString(reg_counter++);
        String l1 = "label" + Integer.toString(label_counter++);
        String l2 = "label" + Integer.toString(label_counter++);

        //load size of the array
        myWriter.write("\t"+r1+" = getelementptr "+struct+", "+struct+"* "+array+", i32 0, i32 0\n");
        myWriter.write("\t"+size+" = load i32, i32* "+r1+"\n");

        //check if index is within bounds
        myWriter.write("\t"+r3+" = icmp sge i32 " + index +", 0\n");    //check if index is greater than zero
        myWriter.write("\t"+r4+" = icmp slt i32 "+ index +", " + size+"\n"); //check if index is less than the size of the array
        myWriter.write("\t" + r5+ " = and i1 " +r3 +", " +r4+"\n");
        myWriter.write("\tbr i1 " + r5 + ", label %"+l1+ ", label %"+l2+"\n");
        myWriter.write(l2+":\n");
        myWriter.write("\tcall void @throw_oob()\n");
        myWriter.write("\tbr label %" + l1+"\n");

        //get address of element
        myWriter.write(l1+":\n");
        myWriter.write("\t"+r6+" = getelementptr "+struct+", "+struct+"* "+array+", i32 0, i32 1\n"); //get address of array
        myWriter.write("\t"+r8+" = load "+type+"*, "+type+"** "+r6+"\n");
        myWriter.write("\t"+r7+" = getelementptr "+type+", "+type+"* "+r8+", i32 "+index+"\n");  //get address of element
        myWriter.write("\t"+r2+" = load "+type+", "+type+"* "+r7+"\n");    //store the value to that address
        return r2;
    }

    /**
    * f0 -> "if"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> Statement()
    * f5 -> "else"
    * f6 -> Statement()
    */
    @Override
    public String visit(IfStatement is, String argu) throws Exception {
        String reg = convert_to_register(is.f2.accept(this, null));
        String if_label = "label" + Integer.toString(label_counter++);
        String else_label = "label" + Integer.toString(label_counter++);
        String other = "label" + Integer.toString(label_counter++);
        myWriter.write("\tbr i1 " + reg + ", label %" + if_label + ", label %" + else_label +"\n");
        myWriter.write(if_label + ":\n");
        is.f4.accept(this, null);
        myWriter.write("\tbr label %" + other + "\n");
        myWriter.write(else_label + ":\n");
        is.f6.accept(this, null);
        myWriter.write("\tbr label %" + other + "\n");
        myWriter.write(other + ":\n");
        return null;
    }

    /**
    * f0 -> "while"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> Statement()
    */
    @Override
    public String visit(WhileStatement ws, String argu) throws Exception {
        String end = "label" + Integer.toString(label_counter++);
        String body = "label" + Integer.toString(label_counter++);
        String top = "label" + Integer.toString(label_counter++);

        myWriter.write("\tbr label %" + top + "\n");
        myWriter.write(top + ":\n");
        String expr = ws.f2.accept(this,null);
        expr = convert_to_register(expr);
        myWriter.write("\tbr i1 " + expr + ", label %" + body + ", label %" + end +"\n");
        myWriter.write(body + ":\n");
        ws.f4.accept(this, null);
        myWriter.write("\tbr label %" + top + "\n");
        myWriter.write(end + ":\n");
        return null;
    }
}