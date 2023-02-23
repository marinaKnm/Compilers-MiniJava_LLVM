import syntaxtree.*;
import visitor.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
public class Main {
    public static void main(String[] args) throws Exception {
        if(args.length < 1){
            System.err.println("Usage: java Main <inputFile1> <inputFile2> ...");
            System.exit(1);
        }

        FileInputStream fis = null;
        try {
            for (int i=0; i<args.length; i++) {
                fis = new FileInputStream(args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);

                Goal root = parser.Goal();

                System.err.println("Program ["+ args[i] +"] parsed successfully.");
                
                DeclCollector eval = new DeclCollector();
                try {
                    root.accept(eval, null);      
                    // System.out.println(eval.class_fields);
                    // System.out.println(eval.class_methods);
                    eval.create_offsets();
                    // System.out.println(eval.class_method_args);////////////////////
                    // System.out.println(eval.variable_offset);
                    // System.out.println(eval.method_offset);
                    eval.print_offsets();
                    String fileName = args[i].substring(0, args[i].length()-4);  //remove postfix "java"

                    Generator g = new Generator(eval, fileName);
                    root.accept(g, null);
                    g.myWriter.close();                
                } catch(Exception e) {
                    System.err.println("Something went wrong while generating IR for input program ["+ args[i] +"] !");
                    try {
                        if(fis != null) fis.close();
                    } catch(IOException ex) {
                        System.err.println(ex.getMessage());
                    }
                }
                try{
                    if(fis != null) fis.close();
                } catch(IOException ex) {
                    System.err.println(ex.getMessage());
                }
            }
        }
        catch(ParseException ex){
            System.out.println(ex.getMessage());
        }
        catch(FileNotFoundException ex){
            System.err.println(ex.getMessage());
        }
        
    }
}