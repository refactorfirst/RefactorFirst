package com.ideacrest.parser.kotlin.sourcepath

/**
 * Kotlin source-path mapping fixture.
 *
 * Used by `KotlinSourcePathMappingTest` to validate that when the
 * repository path contains the `junit-` sentinel (same sentinel the Java
 * visitor uses to switch into the synthetic-path branch), the Kotlin
 * dependency visitor derives a class -> source-path mapping entry whose
 * path ends in `.kt` (the per-language hook defined by
 * `KotlinDependencyVisitor.sourceFileExtension()`).
 *
 * The companion `InnerKt` nested class is what the test inspects: the
 * outer-class FQN carries a `$` separator in the synthetic path branch
 * (mirroring the Java visitor's behaviour, which also keeps the `$` on
 * inner-class FQNs in `recordClassLocation`).
 */
class SourcePathSampleKt {

    fun simpleMethod(): Int = 1

    inner class InnerKt {
        fun helperMethod(): String = "hello"
    }
}
