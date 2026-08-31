package com.example.parity

/**
 * Kotlin parity fixture for DependencyVisitorLogicJavaKotlinParityTest.
 * Mirrors the Java fixture in src/test/resources/parity/java/com/example/parity/ParitySample.java
 * Focuses on J-level features that are known to be identical between Java and Kotlin visitors:
 * visitMethodInvocation, visitNewClass, visitInstanceOf, visitTypeCast, visitNewArray,
 * visitVariableDeclarations, visitClassDeclaration, visitMethodDeclaration, visitMemberReference
 */
class ParitySample {

    fun simpleMethod() {
        val helper = Helper()
        helper.doSomething()
    }

    inner class InnerClass {
        fun helperMethod(): String = "hello"
    }
}

class Helper {
    fun doSomething() {
        println("Helper doing something")
    }
}

interface Service {
    fun execute()
}

class ServiceImpl : Service {
    override fun execute() {
        Helper().doSomething()
    }
}

data class DataRecord(val name: String, val value: Int, val helper: Helper)

class GenericContainer<T : Service> {
    private var item: T? = null

    fun setItem(item: T) {
        this.item = item
    }

    fun getItem(): T? = item
}

class AnnotatedClass {
    @Deprecated
    fun deprecatedMethod() {}

    @Suppress("UNCHECKED_CAST")
    fun <E> uncheckedCast(obj: Any): E = obj as E
}

class InstanceOfUser {
    fun checkType(obj: Any) {
        if (obj is Helper) {
            val h = obj as Helper
            h.doSomething()
        }
    }
}

class ArrayUser {
    fun useArray() {
        val helpers = arrayOfNulls<Helper>(10)
        helpers[0] = Helper()
    }
}

class VariableDeclarationsUser {
    fun useVariables() {
        val h1 = Helper()
        val h2 = Helper()
        val h3 = Helper()
    }
}

class MethodRefUser {
    fun useMethodRef() {
        val h = Helper()
        val r = h::doSomething
        r.run()
    }
}