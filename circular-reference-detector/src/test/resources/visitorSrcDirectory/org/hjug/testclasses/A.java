package org.hjug.testclasses;

import java.util.Map;

public class A<E> implements F<E> extends G {

	@MyAnnotation
	@MyOtherAnnotation
	int intVar, intVar2;
	@MyAnnotation
	@MyOtherAnnotation
	G g;
	A<E> a;
	B<E> b, b3;
	C<E> c;
	D[] ds;

	C doSomething(D d) {
		A<E> a2;
		B<E> b2;
		C<E> c2;
		E e;
		F<E> f;
		return new C();
	}

	class InnerClass{
		class InnerInner{
			class MegaInner{
				G g;
			}
		}
	}
	static class StaticInnerClass{}
}

class NonPublic {}
