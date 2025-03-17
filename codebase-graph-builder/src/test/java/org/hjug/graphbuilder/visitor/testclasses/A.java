package org.hjug.graphbuilder.visitor.testclasses;

import java.util.List;
import java.util.Map;

@MyAnnotation
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
    C<B.InnerB, B> c;
    D[] ds;
    D d;

    @MyAnnotation
    B<C>[] arrayOfGenericBsWithCTypeParam;

    @MyAnnotation
    B<C[]> bWithArrayOfCs;

    List<A<B>> listWithNestedGenric;
    Map<A, B> map;
    List<? extends List<? extends Number>> listOfListsOfNumbers;

    @MyAnnotation
    <T extends A, U extends B> F doSomething(B<C> paramB, C<B.InnerB, B> genericParam) {
        List<B<E>> list3;
        A<E> a2;
        B<C> b2;
        C<A, B> c2;

        H h = new H();

        B.<H>invocationTest(h);

        return new G();
    }

    class InnerClass {
        class InnerInner {
            class MegaInner {
                D d;
            }
        }
    }

    static class StaticInnerClass {}
}

class NonPublic {}
