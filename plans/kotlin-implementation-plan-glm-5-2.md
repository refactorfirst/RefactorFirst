# Kotlin Implementation Plan for RefactorFirst

## Overview

This document outlines the implementation plan to add Kotlin codebase analysis support to RefactorFirst. The plan is
based on the existing Java analysis architecture in the `codebase-graph-builder` module.

## Key Architectural Findings

### OpenRewrite Kotlin Parser API

- **KotlinParser.builder().build()** - Analogous to `JavaParser.fromJavaVersion().build()`
- Returns `Stream<SourceFile>` from `parseInputs()`
- Supports `.kt` and `.kts` file extensions
- Uses `org.openrewrite.kotlin.KotlinParser.KotlinLanguageLevel` enum (default: `KOTLIN_2_2`)

### Visitor Architecture

- **KotlinVisitor<P> extends JavaVisitor<P>** (OpenRewrite's JavaVisitor)
- **KotlinIsoVisitor<P> extends KotlinVisitor<P>**
- J-level overrides (`visitClassDeclaration(J.ClassDeclaration,...)`, `visitMethodDeclaration`,
  `visitVariableDeclarations`, `visitMethodInvocation`, `visitMemberReference`, `visitIdentifier`, `visitFieldAccess`)
  are inherited and dispatched when walking `K.CompilationUnit`
- Kotlin AST wraps `J.*` nodes inside `K.*` wrappers (`K.ClassDeclaration`→`J.ClassDeclaration`, `K.Property`→
  `J.VariableDeclarations`, `K.MethodDeclaration`→`J.MethodDeclaration`)
- Type system: produces `org.openrewrite.java.tree.JavaType` (FullyQualified/Method/Variable) — existing
  `TypeDependencyExtractor` and `BaseTypeProcessor` work unchanged

### Dependency Management

- `rewrite-bom:8.86.0` (imported via `rewrite-recipe-bom:3.34.0`) manages `org.openrewrite:rewrite-kotlin:8.86.0`
- Kotlin compiler-embeddable is a transitive dependency (~30 MB) — make optional
- Java 17 compatible (Kotlin stdlib targets JVM 1.8)

## Design Strategy

- Reuse over fork: Introduce language-agnostic graph-builder with per-language strategies
- Existing `JavaGraphBuilder` becomes thin facade; new `CompositeGraphBuilder` orchestrates
- Abstract visitor base with protected hooks shared between Java/Kotlin visitors
- Kotlin test fixtures as plain-text resources (NOT compiled Kotlin source)

## Implementation Phases

### Phase 0 — Dependency Wiring ✓

- Add `rewrite-kotlin` dependency to `../codebase-graph-builder/pom.xml`
- Verify `mvn -pl codebase-graph-builder compile` under JDK 17

### Phase 1 — Kotlin-Only Parsing

**Red Test**: `KotlinGraphBuilderTest.parseKotlinSourceDirectoryTest` — mirrors Java test on `.kt` fixtures  
**Production**:

- Create `AbstractDependencyVisitor<P>` with protected J-level hooks
- Refactor `JavaVisitor` to extend `AbstractDependencyVisitor`
- Create `KotlinDependencyVisitor<P> extends KotlinIsoVisitor<P>` with K-level overrides
- Create `KotlinSourceFileGraphBuilder` implementing `SourceFileGraphBuilder` interface
- Create `KotlinGraphBuilder` facade

### Phase 2 — Mixed Java + Kotlin

**Red Test**: `CompositeGraphBuilderTest` — cross-language edges  
**Production**:

- `CompositeGraphBuilder` walks `.java` and `.kt` files
- Reflectively probes `KotlinParser` presence (optional jar support)
- `GraphBuilderConfig.analyzeKotlin` default `true`

### Phase 3 — Kotlin Metrics Collection

**Red Test**: `KotlinMetricsCollectionTest` — LOC/NOM/NOA/WMC/ATFD/TCC on Kotlin fixtures  
**Production**:

- Refactor `MetricsCollectingVisitor` → `AbstractMetricsCollectingVisitor` (protected hooks)
- Create `KotlinMetricsCollectingVisitor extends KotlinIsoVisitor<ExecutionContext>`
- Handle `K.Property` (top-level properties, extension properties)

### Phase 4 — Callable References

**Red Test**: `KotlinGraphBuilderTest` callable reference fixture  
**Production**:

- Lift `visitMemberReference` to `AbstractDependencyVisitor`
- Resolve `JavaType.Method.getDeclaringType()` / `JavaType.Variable.getOwner()`
- Bump `numberOfCallableReferences` on `ClassMetrics`/`MethodMetrics`
- Record `calledForeignMethods`/`calledForeignMethodClasses` for Shotgun Surgery

### Phase 5 — Type Parameters & Type Aliases

**Red Test**: `TypeParameterReferenceTest`  
**Production**:

- `KotlinDependencyVisitor.visitMethodDeclaration(K.MethodDeclaration)` extracts type params from K wrapper
- `visitTypeAlias(K.TypeAlias)` processes type alias parameters
- `KotlinMetricsCollectingVisitor` records `typeParameterFqns` on metrics

### Phase 6 — Kotlin-Specific Disharmonies (ClassDisharmony)

Four new disharmony types:

| Disharmony             | Constant                 | Detection Logic                                          |
|------------------------|--------------------------|----------------------------------------------------------|
| God Object (Kotlin)    | `GOD_CLASS` (reused)     | Existing thresholds + extension-function count           |
| Excessive Extensions   | `EXCESSIVE_EXTENSIONS`   | ≥10 extension functions across ≥5 foreign receiver types |
| Large Sealed Hierarchy | `LARGE_SEALED_HIERARCHY` | Sealed type with ≥12 permitted subtypes in codebase      |
| Data Class with Logic  | `DATA_CLASS_WITH_LOGIC`  | `isDataClass && (hasExplicitLogic\|\|WMC > 14)`          |

**Additive ClassMetrics fields**: `numberOfExtensionFunctions`, `numberOfCallableReferences`,
`sealedHierarchyAncestors`, `sealedHierarchyDepth`, `isDataClass`, `hasExplicitLogic`

### Phase 7 — Disharmony Detection Parity

- Run all 11 existing disharmony detectors against Kotlin fixtures
- Tune `KotlinMetricsCollectingVisitor` until green

### Phase 8 — Source Path Mapping

- Extract `sourceFileExtension()` hook in `AbstractDependencyVisitor`
- `.kt` for Kotlin, `.java` for Java

### Phase 9 — CycleRanker Round-Trip

- `CycleRanker` uses new `CodebaseGraphBuilder` orchestrator
- Verify `rankCycles` works on Kotlin repos

### Phase 10 — Reporting Smoke Test

- `HtmlReportTest` Kotlin case
- Expected no change to `SimpleHtmlReport`

### Phase 11 — Build & Lint Hygiene

- `mvn spotless:check`, full build, OWASP check
- Pin transitive CVEs in parent `<dependencyManagement>`

## Backward Compatibility

- `JavaGraphBuilder.getCodebaseGraphDTO(String, boolean, String)` preserved
- `CycleRanker.generateClassReferencesGraph(boolean, String)` preserved
- `CodebaseGraphDTO` unchanged
- `GraphBuilderConfig` additions are `@Builder.Default` additive

## Test Fixture Strategy

- All `.kt` fixtures under `src/test/resources/kotlinSrcDirectory/` and `src/test/java/.../testkotlin/`
- Treated as plain-text classpath resources — Kotlin compiler plugin NEVER invoked
- Build remains Java-only; Spotless ignores `.kt` files

## Locked Design Decisions

1. **Refactor** J-level logic into protected hooks on abstract bases (no fork-and-drift)
2. **Optional Maven dependency** — `rewrite-kotlin` marked `<optional>true</optional>`
3. **Kotlin language level**: `KOTLIN_2_2` (parser default), configurable via `GraphBuilderConfig`
4. **Kotlin disharmonies as ClassDisharmony** — reuses existing downstream plumbing
5. **Callable references & type parameters feed BOTH graph edges AND metrics**