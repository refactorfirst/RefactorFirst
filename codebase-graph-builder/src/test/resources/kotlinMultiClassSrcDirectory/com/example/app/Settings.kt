package com.example.app

/**
 * Kotlin multi-class-per-file fixture for testing source path mapping.
 * Mimics FXGL's structure where GameSettings is in Settings.kt
 */

class GameSettings {
    var difficulty: String = "normal"
    var volume: Float = 1.0f
}

class OtherSettings {
    var theme: String = "dark"
    var language: String = "en"
}

object TopLevelObject {
    fun greet(): String = "hello"
}

sealed class SealedExample {
    data class VariantA(val value: Int) : SealedExample()
    data class VariantB(val name: String) : SealedExample()
}

interface ServiceInterface {
    fun execute()
}

class ServiceImplementation : ServiceInterface {
    override fun execute() {
        println("executing")
    }
}

// Companion object must be inside a class
class ClassWithCompanion {
    companion object {
        const val CONSTANT = "test"
    }
}