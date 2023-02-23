import syntaxtree.*;
import visitor.GJDepthFirst;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.*;

enum MyType {
    BOOLEAN,
    INT,
    INT_ARRAY,
    BOOLEAN_ARRAY
}

public class DeclCollector extends GJDepthFirst<String, String> { 
    Map <String, Map<String, String>> class_fields;   //<ClassName, <VarName, Type>>
    Map <String, String> inherits_from;             //<ClassName, ClassName>
    String stack_fields;                           //used for VarDeclaration for when we have consequent declarations of variables

    //for method declarations
    // Map <String, MethodDecl> class_methods;  //<ClassName, MethodDecl>                    
    Map <String, Map <String, String> > temp_class_methods;//<MethodName, <VarName, Type> > => OUTER = LINKED HASH MAP
    Map <String, Map <String, Map <String, String> > > class_methods;   //<ClassName, <MethodName, <VarName, Type> > >
    Map <String, Map <String, Map <String, String> > > class_method_args;//this map will hold only the arguments of a particular method  <ClassName, <MethodName, <VarName, Type> > >
    Map <String, Map <String, String> > temp_method_args;

    //maps for offsets
    Map <String, Map<String, Integer>> variable_offset; //<ClassName, <VarName, offset>>
    Map <String, Map<String, Integer>> method_offset;   //<ClassName, <MethodName, offset>>


    public DeclCollector() {
        inherits_from = new HashMap<String, String>();
        class_fields = new LinkedHashMap<>();
        ////////////
        stack_fields = "";
        class_methods = new HashMap<>();
        temp_class_methods = null;
        class_method_args = new LinkedHashMap<>();
        temp_method_args = null;
        
        variable_offset = new HashMap<>();
        method_offset = new HashMap<>();
    }

    public String convert_to_MyType(String s) {
        String res = null;
        if("int".equals(s)) res = MyType.INT.toString();//res = MyType.INT;
        else if("boolean".equals(s)) res = MyType.BOOLEAN.toString();
        else if("boolean[]".equals(s)) res = MyType.BOOLEAN_ARRAY.toString();
        else if("int[]".equals(s)) res = MyType.INT_ARRAY.toString();
        else res = s;
        return res;
    }

    //general function for declaration of a class 
    public void Decl_class(NodeListOptional f, String class_name) throws Exception {
        String ss;
        if (f.present()) { //if there are variables declared
            ss = f.accept(this, null);

            String[] fields = stack_fields.split("[ ;]");   //fields[even number] -> type, fields[odd number] -> variable
            Map<String, String> inner = new LinkedHashMap<String, String>();   //linked because we want to keep the order of the variable declarations for the output later
            for (int i=0; i<fields.length; i=i+2) {
                if (inner.containsKey(fields[i+1])) throw new Exception();  //if a variable with the same name already exists in this class throw exception
                
                //create inner map objects
                inner.put(fields[i+1], convert_to_MyType(fields[i]));
                // System.out.print(inner);
            }
            
            class_fields.put(class_name, inner);
            

            stack_fields = "";
        } else {
            //insert class name with no field
            class_fields.put(class_name, null);
        }

        ////// PRINT MAPS FOR FIELDS AND INHERITANCE //////////
        // System.out.println("<className, Fields>"+class_fields);
        // System.out.println("cn1 inherits from cn2 ->"+inherits_from);
        
    }

    /**
    * f0 -> MainClass()
    * f1 -> ( TypeDeclaration() )*
    * f2 -> <EOF>
    */
    @Override
    public String visit(Goal g, String argu) throws Exception {
        //recursive calls
        g.f0.accept(this,null);
        if (g.f1.present()) g.f1.accept(this,null);

        //check that every declaration of a variable of type [class] corresponds to a declared class

        //for variables declared in a class
        for (Map.Entry<String,Map<String, String>> entry : class_fields.entrySet()) {
            // System.out.println("Key = " + entry.getKey() +
            //                  ", Value = " + entry.getValue());

            String class_name = entry.getKey();
            Map<String, String> variables = entry.getValue();

            if (variables == null) continue;    //if this class has no variables continue

            for (Map.Entry<String,String> inner_entry : variables.entrySet()) {
                // System.out.println("\tKey = " + inner_entry.getKey() +
                //                 ", Value = " + inner_entry.getValue());

                String type = inner_entry.getValue();
                if (!type.equals("BOOLEAN") && !type.equals("INT") && !type.equals("INT_ARRAY") && !type.equals("BOOLEAN_ARRAY")) {
                    if(!class_fields.containsKey(type)) {
                        throw new Exception();
                    }
                }
            }
        }
        // System.out.print(class_fields);

        //for variables declared in a method
        for (Map.Entry<String, Map<String, Map<String, String>>> entry : class_methods.entrySet()) {
            
            Map<String, Map<String, String>> method = entry.getValue();
            if (method == null) continue;  //if class has no method

            for (Map.Entry<String, Map<String, String>> inner1_entry : method.entrySet()) {

                Map<String, String> variables = inner1_entry.getValue();

                if (variables == null) continue;    //if this class has no variables continue

                for (Map.Entry<String,String> inner2_entry : variables.entrySet()) {

                    String type = inner2_entry.getValue();
                    if (type == null) continue; //if this is main method skip loop
                    if (!type.equals("BOOLEAN") && !type.equals("INT") && !type.equals("INT_ARRAY") && !type.equals("BOOLEAN_ARRAY")) {
                        if(!class_fields.containsKey(type)) {
                            throw new Exception();
                        }
                    }
                }
            }
        }
        // System.out.print(class_methods);
        // System.out.print(inherits_from);
        // System.out.println(class_fields);
        return null;
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
        String class_name;
        class_name = mc.f1.accept(this,null);
        String args = mc.f11.accept(this, null);

        //this has main function declared
        temp_class_methods = new LinkedHashMap<>();
        Map<String,String> inner = new HashMap<>(); //<VarName, Type>

        //insert argument of main function into the hashmap
        inner.put(args, null);


        if (mc.f14.present()) { //if there are variables declared in this function
            // stack_fields = "";
            
            mc.f14.accept(this, null);

            String[] fields = stack_fields.split("[ ;]");   //fields[even number] -> type, fields[odd number] -> variable
            
            for (int i=0; i<fields.length; i=i+2) {
                if (inner.containsKey(fields[i+1])) throw new Exception();  //if a variable with the same name already exists in this method of the class throw exception
                
                //create inner map objects
                inner.put(fields[i+1], convert_to_MyType(fields[i]));
            }
            stack_fields = "";
        }
        temp_class_methods.put("MAIN", inner);
        class_methods.put(class_name, temp_class_methods);

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
        String class_name = cd.f1.accept(this, null);

        //if this class is already contained in the map throw exception
        if (class_fields.containsKey(class_name)) throw new Exception();

        Decl_class(cd.f3, class_name);

        ///////////////////////////////
        if(cd.f4.present()) {   //if this class has function declarations
            temp_class_methods = new LinkedHashMap<>();
            temp_method_args = new LinkedHashMap<>();
            String s = cd.f4.accept(this,class_name);
            class_methods.put(class_name, temp_class_methods);  //insert this class name into the hasmap of methods
            class_method_args.put(class_name, temp_method_args); 
        } else {
            class_methods.put(class_name, null);
            class_method_args.put(class_name, null);
        }
        
        // System.out.print(class_methods);
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
        String superclass = ced.f3.accept(this, null);
        String subclass = ced.f1.accept(this, null);

        //if this class is already contained in the map throw exception (check for classes with the same name)
        if (class_fields.containsKey(subclass)) throw new Exception();

        //check if the superclass has already been declared, if not throw exception
        if (!class_fields.containsKey(superclass)) throw new Exception();

        //insert this type or inheritance into the hashmap
        inherits_from.put(subclass, superclass);    //no need to check if the subclass already exists, this is taken care by the grammar
        Decl_class(ced.f5, subclass);

        if(ced.f6.present()) {   //if this class has function declarations
            temp_class_methods = new LinkedHashMap<>();
            temp_method_args = new LinkedHashMap<>();
            String s = ced.f6.accept(this, subclass);
            class_methods.put(subclass, temp_class_methods);  //insert this class name into the hasmap of methods
            class_method_args.put(subclass, temp_method_args);
        } else {
            class_methods.put(subclass, null);
            class_method_args.put(subclass, null);
        }
        
        // System.out.print(class_methods);

        return null;/////////////////////////RETURN?????
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
        String type = md.f1.accept(this,null);
        String method_name = md.f2.accept(this,null);

        if (temp_class_methods.containsKey(method_name)) throw new Exception();    //function of the same name in a certain class are not allowed 


        Map<String,String> inner = new HashMap<>(); //<VarName, Type>
        inner.put("RETURN", convert_to_MyType(type));   //[special case] insert return type
        
        Map <String, String> args = new LinkedHashMap();

        if (md.f4.present()) {    //if this method has arguments
            String w = md.f4.accept(this, null);
            //PUT ARGUMENTS INTO THE MAP OF THE METHOD
            // System.out.println("FUNCTION: " + method_name + " ARGS: "+ stack_fields);
            /////////////////////////////////////////////////////
            String[] fields = stack_fields.split("[ ;]");   //fields[even number] -> type, fields[odd number] -> variable
            for (int i=0; i<fields.length; i=i+2) {                
                //create inner map objects
                args.put(fields[i+1], convert_to_MyType(fields[i]));
            }
            
        } else {
            args = null;
        }
        //insert agruments of method into its corresponding map
        temp_method_args.put(method_name, args); 


        if (md.f7.present()) {
            md.f7.accept(this, null); 
        }

        if (md.f7.present() || md.f4.present()) { //if there are variables declared in this function

            String[] fields = stack_fields.split("[ ;]");   //fields[even number] -> type, fields[odd number] -> variable
           
            for (int i=0; i<fields.length; i=i+2) {
                if (inner.containsKey(fields[i+1])) throw new Exception();  //if a variable with the same name already exists in this method of the class throw exception
                
                //create inner map objects
                inner.put(fields[i+1], convert_to_MyType(fields[i]));
            }
            stack_fields = "";
        }
        temp_class_methods.put(method_name, inner);
        md.f10.accept(this,null);
 
        return null;
    }

    /**
    * f0 -> FormalParameter()
    * f1 -> FormalParameterTail()
    */
    @Override
    public String visit(FormalParameterList fpl, String argu) throws Exception {
        fpl.f0.accept(this, null);
        fpl.f1.accept(this, null);
        return "";
    }

    /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
    @Override
    public String visit(FormalParameter fp, String argu) throws Exception{
        String type = fp.f0.accept(this, null);
        // System.out.println("Type: " + type);
        String var = fp.f1.accept(this, null);
        // System.out.println("Field: " + var);
        stack_fields = stack_fields + type + " " + var + ";";
        return "";
    }

    /**
    * f0 -> ( FormalParameterTerm() )*
    */
    @Override
    public String visit(FormalParameterTail fp, String argu) throws Exception{
        if (fp.f0.present())  fp.f0.accept(this, null);
        return "";
    }

    /**
    * f0 -> ","
    * f1 -> FormalParameter()
    */
    @Override
    public String visit(FormalParameterTerm fp, String argu) throws Exception{
        fp.f1.accept(this, null);
        return "";
    }

    /**
    * f0 -> <IDENTIFIER>
    */
    @Override 
    public String visit(Identifier i, String argu) {
        String s = i.f0.toString();
        // System.out.println(s);
        return s;
    }

    /**
    * f0 -> "int"
    */
    @Override
    public String visit(IntegerType it, String argu) throws Exception{
        String type = it.f0.toString();
        // System.out.println(type);
        return type;
    }

    /**
    * f0 -> "boolean"
    */
    @Override
    public String visit(BooleanType bt, String argu) throws Exception{
        String type = bt.f0.toString();
        // System.out.println(type);
        return type;
    }

    /**
    * f0 -> BooleanArrayType()
    *       | IntegerArrayType()
    */
    @Override
    public String visit(ArrayType at, String argu) throws Exception{
        String type = at.f0.accept(this, null);
        // System.out.print(type);
        return type;
    }

    /**
    * f0 -> "boolean"
    * f1 -> "["
    * f2 -> "]"
    */
    @Override
    public String visit(BooleanArrayType bt, String argu) throws Exception{
        String type = bt.f0.toString() + "[]";
        // System.out.print(type);
        return type;
    }

    /**
    * f0 -> "int"
    * f1 -> "["
    * f2 -> "]"
    */
    @Override
    public String visit(IntegerArrayType it, String argu) throws Exception{
        String type = it.f0.toString() + "[]";
        // System.out.print(type);
        return type;
    }

    /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
    @Override
    public String visit(VarDeclaration vd, String argu) throws Exception{
        String type = vd.f0.accept(this, null);
        // System.out.println("Type: " + type);
        String var = vd.f1.accept(this, null);
        // System.out.println("Field: " + var);
        stack_fields = stack_fields + type + " " + var + ";";
        return "";
    }

    public void print_offsets() {
        int index=0;    
        //for every class declared
        for (Map.Entry<String, Map<String, String>> entry : class_fields.entrySet()) {
            String className = entry.getKey();

            //loop through the map of variable-offset (if this class has any variables)
            Map <String, Integer> var_offsets = variable_offset.get(className);
            if(var_offsets != null) {
                for (Map.Entry<String, Integer> entry1 : var_offsets.entrySet()) {
                    if(entry1.getValue() != null) {
                        System.out.println(className + "." + entry1.getKey() +": "+ entry1.getValue());
                    }
                }
            }

            //loop through the map of method-offset (if this class has any methods)
            Map <String, Integer> method_offsets = method_offset.get(className);
            if(method_offsets != null) {
                for (Map.Entry<String, Integer> entry1 : method_offsets.entrySet()) {
                if(entry1.getValue() != null) {
                    System.out.println(className + "." + entry1.getKey() +": "+ entry1.getValue());
                }
                }
            }
        }
    }

    public void create_offsets() {
        int indexv = 0;
        int indexf = 0;

        //for every class
        for (Map.Entry<String, Map<String, String>> entry : class_fields.entrySet()) {
            String className = entry.getKey();

            //if this class does not inherit from another class set index to 0
            if(!inherits_from.containsKey(className)) {
                indexv = 0;
            }

            Map<String, Integer> inner = new LinkedHashMap<>();

            //----------VARIABLES OF CLASS------------
            Map<String, String> variables = entry.getValue();
            if(variables != null) {

                for(Map.Entry<String, String> entry1 : variables.entrySet()) {
                    inner.put(entry1.getKey(), indexv);

                    String type = entry1.getValue();
                    if(type.equals("INT")) indexv+=4;
                    else if(type.equals("BOOLEAN")) indexv +=1;
                    else indexv +=8;
                }
            } else {
                inner = null;
            }

            variable_offset.put(className, inner);

            if(!inherits_from.containsKey(className)) {
                indexf = 0;
            }

            Map<String, Integer> inner1 = new LinkedHashMap<>();
            //------------- METHODS OF CLASS--------------
            Map<String, Map<String, String>> methods = class_methods.get(className);
            if(methods != null) {
                for (Map.Entry<String, Map<String, String>> entry2 : methods.entrySet()) {
                    String methodName = entry2.getKey();
    
                    //if this function is not overriden
                    if ((inherits_from.containsKey(className) && !isOverriden(className, methodName)) || !inherits_from.containsKey(className)) {
                        inner1.put(methodName, indexf);
                        indexf += 8;
                    } else if(inherits_from.containsKey(className) && isOverriden(className, methodName)) {
                        inner1.put(methodName, null);   //οverriden functions are set to offset = null
                    }
                }
            } else {
                inner1 = null;
            }

            method_offset.put(className, inner1);
        }
    }

    public boolean isOverriden(String className, String MethodName) {
        boolean flag = false;
        String superClass = inherits_from.get(className);
        
        if(superClass != null) {
            Map <String, Map<String, String>> methods = class_methods.get(superClass);
            if (methods != null) {
                if (methods.containsKey(MethodName)) {
                    flag = true;
                } else {
                    flag = isOverriden(superClass, MethodName);
                }
            }
        }

        return flag;
    }
}