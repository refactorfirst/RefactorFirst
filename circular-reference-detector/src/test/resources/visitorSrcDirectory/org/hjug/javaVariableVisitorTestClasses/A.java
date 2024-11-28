package org.hjug.javaVariableVisitorTestClasses;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;

public class A<T> {

//	public A(B cB, C cC){}

	B<? extends C> crazyType;

	@MyAnnotation
	@MyOtherAnnotation
	int intVar, intVar2;
	@MyAnnotation
	@MyOtherAnnotation
	C rawC;
	B<B> b, b3;
	C<B.InnerB, C> c;
	D[] ds;

	@MyAnnotation
	B<C>[] arrayOfGenericBsWithCTypeParam;
	@MyAnnotation
	B<C[]> bWithArrayOfCs;
	List<A<B>> listWithNestedGenric;
	Map<A,B> map;
	List<? extends List<? extends Number>> listOfListsOfNumbers;

	C doSomething(@NonNull B<C> paramB) {
		List<B<E>> list3;
		A<E> a2;
		B<C> b2;
		C<A,B> c2;
		return new C();
	}

	class InnerClass{
		class InnerInner{
			class MegaInner{
				 D d;
			}
		}
	}
	static class StaticInnerClass{}
}

class NonPublic {}
