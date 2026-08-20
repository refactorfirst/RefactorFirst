package com.ideacrest.parser.callref

class CallableRefUser {
    val alphaRef = CallableRefTarget::alpha

    val betaRef = CallableRefTarget::beta

    fun useRefs(target: CallableRefTarget): Int {
        val a = alphaRef.call(target)
        return a
    }

    fun methodScopedRefs(): List<Any> {
        val aRef = CallableRefTarget::alpha
        val bRef = CallableRefTarget::beta
        return listOf(aRef, bRef)
    }
}
