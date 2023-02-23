class Arrays {
	public static void main(String[] args) {
		int[] x;
		int[] y;
		int b;
		A a;
		boolean k;
		k=true;
	    // b = 3-10;
	    x = new int[7];
		x[0] = 100;
        x[1] = 2;
		x[3] = 1998;
		y = x;
		
		System.out.println(y[3]);
		// x[98]=3;
		a = new A();
		System.out.println(a.goo());
		System.out.println(a.la());
		System.out.println(x.length);
		System.out.println(x[0]);
		
	}
}

class A {
	boolean[] b;
	int[] i;

	public int goo() {
		int x;
		b = new boolean[3];
		i = new int[1];
		i[0]=223;
		x=i.length;
		System.out.println(b.length);
		System.out.println(i.length);

		return x;
	}
	public int la() {
		return i[0];
	}
}
