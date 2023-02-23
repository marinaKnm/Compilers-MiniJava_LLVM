class Alsdfjasdjfl {
  public static void main(String[] irrelevant) {
    boolean dummy;
    A a;
    a = new A();
    dummy = a.print(a.foo(false, false, false));	//0 -
    dummy = a.print(a.foo(false, false, true));		//0 -
    dummy = a.print(a.foo(false, true, false));		//0 -
    dummy = a.print(a.foo(false, true, true));		//0 -
    dummy = a.print(a.foo(true, false, false));		//0 
    dummy = a.print(a.foo(true, false, true));		//0
    dummy = a.print(a.foo(true, true, false));		//0
    dummy = a.print(a.foo(true, true, true));			//1

  }
}

class A {
  public boolean foo(boolean a, boolean b, boolean c) { return (a && b) && c; }

  public boolean print(boolean res) {
    if (res) {
      System.out.println(1);
    } else {
      System.out.println(0);
    }
    return true;
  }
}
