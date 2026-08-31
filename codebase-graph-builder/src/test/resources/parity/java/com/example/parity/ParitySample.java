package com.example.parity;

/**
 * Java parity fixture for DependencyVisitorLogicJavaKotlinParityTest.
 * Mirrors the Kotlin fixture in src/test/resources/parity/kotlin/com/example/parity/ParitySample.kt
 * Focuses on J-level features that are known to be identical between Java and Kotlin visitors:
 * visitMethodInvocation, visitNewClass, visitInstanceOf, visitTypeCast, visitNewArray,
 * visitVariableDeclarations, visitClassDeclaration, visitMethodDeclaration, visitMemberReference
 */
public class ParitySample {

    public void simpleMethod() {
        Helper helper = new Helper();
        helper.doSomething();
    }

    public static class InnerClass {
        public String helperMethod() {
            return "hello";
        }
    }
}

class Helper {
    public void doSomething() {
        System.out.println("Helper doing something");
    }
}

interface Service {
    void execute();
}

class ServiceImpl implements Service {
    @Override
    public void execute() {
        new Helper().doSomething();
    }
}

record DataRecord(String name, int value, Helper helper) {
}

class GenericContainer<T extends Service> {
    private T item;

    public void setItem(T item) {
        this.item = item;
    }

    public T getItem() {
        return item;
    }
}

class AnnotatedClass {
    @Deprecated
    public void deprecatedMethod() {
    }

    @SuppressWarnings("unchecked")
    public <E> E uncheckedCast(Object obj) {
        return (E) obj;
    }
}

class InstanceOfUser {
    public void checkType(Object obj) {
        if (obj instanceof Helper) {
            Helper h = (Helper) obj;
            h.doSomething();
        }
    }
}

class ArrayUser {
    public void useArray() {
        Helper[] helpers = new Helper[10];
        helpers[0] = new Helper();
    }
}

class VariableDeclarationsUser {
    public void useVariables() {
        Helper h1 = new Helper();
        Helper h2 = new Helper();
        Helper h3 = new Helper();
    }
}

class MethodRefUser {
    public void useMethodRef() {
        Helper h = new Helper();
        Runnable r = h::doSomething;
        r.run();
    }
}