class Example {
    public static void main(String[] args) {
        int x;
        boolean[] arr1;
        int[] arr2;
        boolean b;
        C a;

        a = new B();
        System.out.println(134);
        
    }
}

class C {
    int a;
    public int boo() { return 3; }
    public int foo(int i, int j) { return i+j; }
}

class A {
    int i;
    boolean[] k;
    C c;

    // public int foo(int i, int j) { return i+j; }
    public int bar(){ c = new C(); 
    return 12; }
}
class DD {
    int x;
}
class B extends A{
    // int i;
    // int[] j;
    // C c;

    
    public int foobar(boolean k){ return 1; }
    public int foo(int i, int j) { return i+j; }
}