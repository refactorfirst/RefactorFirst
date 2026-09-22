# JEP 238 (Multi-Release JAR) Implementation Plan: Activate the OpenRewrite Java 25 Parser and Remove All `forceJava25` Flags

## 1. Goal

Replace the reflection-based, detection-gated Java 25 parser activation with a
[JEP 238](https://openjdk.org/jeps/238) Multi-Release (MR) JAR design, and delete
every `forceJava25Parser` / `forceJava25` flag from the codebase.

After this change:

- On a **JDK 25+ runtime**, the OpenRewrite `Java25Parser` is activated
  automatically by the JVM's own multi-release class lookup — no reflection, no
  version-property parsing, no flag.
- On a **JDK 17/21 runtime**, the Java 25 parser classes are never loaded and the
  standard `JavaParser.fromJavaVersion()` parser is used.
- The Java 25 parser actually **ships** in the Maven plugin and CLI
  distributions (today it does not — see §2, problem 3).
- The public API surface is reduced: `forceJava25Parser` disappears from
  `GraphBuilderConfig`, `CycleRanker`, `SimpleHtmlReport`, `JsonGenerator`,
  `HtmlReport`, and all four Maven mojos.

Development follows TDD: every phase writes or updates failing tests first,
then implements until green, on **both** JDK 17 and JDK 25.

## 2. Current State and Its Problems

Current mechanism (all in `codebase-graph-builder` unless noted):

- `rewrite-java-25` is an `<optional>` dependency; its class files are
  class-file 69.0, so nothing at release 17 may reference it statically.
- `Java25ParserWrapper.tryCreateJava25Parser(boolean force)` reflectively loads
  `org.openrewrite.java.Java25Parser`, gated by
  `JavaRuntimeDetector.isJava25OrHigher()` (parses
  `java.specification.version`, falls back to `Runtime.version()`).
- `forceJava25Parser` is a diagnostic escape hatch threaded through:
  - `GraphBuilderConfig.forceJava25Parser`
  - `CycleRanker.generateClassReferencesGraph(boolean, String, boolean)`
    (cost-benefit-calculator)
  - `SimpleHtmlReport.execute(...)` / `generateReport(...)`,
    `JsonGenerator.execute(...)` / `generateReportData(...)`,
    `HtmlReport.generateReport(...)` (report)
  - `@Parameter(property = "forceJava25Parser")` on
    `RefactorFirstMavenReport`, `RefactorFirstHtmlReport`,
    `RefactorFirstSimpleHtmlReport`, `RefactorFirstMavenJsonGenerator`
    (`-DforceJava25Parser=true`)

Problems this plan fixes:

1. **Complexity**: reflection glue, a runtime detector, a cached load-state
   machine, and a boolean threaded through ~10 methods/4 mojos — all to express
   "use this class when the runtime is new enough," which the JVM can express
   natively via MR-JAR lookup.
2. **The force flag is semantically dead**: on a sub-25 runtime the class files
   physically cannot load (`UnsupportedClassVersionError`), so "force" could
   never succeed — it only existed to bypass detection on hypothetical
   misreporting JVMs. MR-JAR lookup is driven by the actual runtime version
   (`Runtime.version()` semantics inside `JarFile`), which is stricter and more
   reliable than any system-property parse, so the escape hatch has no analog
   and no purpose.
3. **The parser never ships**: because `rewrite-java-25` is `<optional>`, it is
   absent from the Maven plugin and CLI fat-jar distributions (verified via
   `mvn -pl refactor-first-maven-plugin dependency:tree`). Even a user running
   Maven on JDK 25 silently gets the Java 21 parser today. The MR-JAR change
   must therefore also fix distribution (Phase 6).

## 3. Design Overview

### 3.1 Core idea

Introduce one seam class with two compiled variants in the same JAR:

```
org.hjug.graphbuilder.graphbuilder.Java25ParserFactory
```

- **Base variant** (`src/main/java`, compiled `--release 17`):
  returns `Optional.empty()`. It contains *no* reference to Java 25 types.
- **Versioned variant** (`src/main/java25`, compiled `--release 25` into
  `META-INF/versions/25/…`): directly calls
  `org.openrewrite.java.Java25Parser.builder().build()` and returns it wrapped
  in `Optional`.

Per JEP 238, when the JAR is on the classpath of a JDK 25+ runtime **and** the
manifest declares `Multi-Release: true`, the versioned class shadows the base
class. On JDK 17/21 the versioned entry is invisible — it can never be loaded,
verified, or linked, so the class-file-69.0 problem disappears by construction.

Parser selection in `JavaSourceFileGraphBuilder.createJavaParser` becomes:

```java
static JavaParser createJavaParser(GraphBuilderConfig config) {
    return Java25ParserFactory.createJava25Parser()
        .orElseGet(() -> JavaParser.fromJavaVersion().build());
}
```

No config flag, no detector, no reflection, no cache. (`GraphBuilderConfig`
retains its other fields; the parameter stays because the method's other
callers/tests use the config object — only the `forceJava25Parser` use is
dropped.)

### 3.2 Public-API parity requirement

JEP 238 requires a versioned class to expose the same public API as the base
class, but we assemble the JAR via the compiler/jar plugins rather than
`jar --release`, so there is **no automatic validation**. Mitigation:

- Keep the class tiny (one public static method + private constructor).
- Add a contract test (Phase 3, T3) asserting base and versioned variants
  expose identical public signatures, run on JDK 25 where both are on disk.

### 3.3 Runtime behavior matrix (acceptance criteria)

| Runtime        | `rewrite-java-25` present | Result                                              |
|----------------|---------------------------|-----------------------------------------------------|
| JDK 17 / 21    | yes (now a real dep)      | Base factory → `Optional.empty()` → standard parser. Version-69 classes never loaded. |
| JDK 25+        | yes                       | Versioned factory → `Java25Parser`                  |
| JDK 25+        | no (broken packaging)     | Versioned factory catches `Throwable` → `Optional.empty()` → standard parser + `log.warn` |

The third row's defensive `catch (Throwable)` replaces the graceful-degradation
guarantee the reflection wrapper provided.

### 3.4 Build mechanics (codebase-graph-builder)

- New source root `src/main/java25` containing only
  `org/hjug/graphbuilder/graphbuilder/Java25ParserFactory.java`.
- A `jdk25+` Maven profile (auto-activated by `<jdk>[25,)</jdk>`) adds a second
  `maven-compiler-plugin` execution: `release=25`, sources from
  `src/main/java25`, output to
  `${project.build.outputDirectory}/META-INF/versions/25`.
- `maven-jar-plugin` gets
  `<archive><manifestEntries><Multi-Release>true</Multi-Release></manifestEntries></archive>`.
- **Release builds must run on JDK 25+** or the published JAR silently lacks
  the versioned entries. `release.yml` currently builds with JDK 17 and must be
  bumped to 25, plus a release-time guard (Phase 6, T12) that fails the build
  if `META-INF/versions/25` is missing from the packaged JAR. CI keeps the
  17 + 25 matrix: JDK 17 proves the fallback side and still produces a valid
  (non-MR) JAR.

### 3.5 Distribution change

Flip `rewrite-java-25` from `<optional>true</optional>` to a normal compile
dependency (still BOM-managed, no explicit version). With the MR-JAR bridge,
nothing on a JDK 17/21 classpath ever loads a version-69 class file, so the
original reason for optionality is gone; keeping it optional would mean the
versioned factory fires on JDK 25 but `Java25Parser` is missing in the very
distributions this feature targets.

*Alternative considered and rejected:* unpacking `rewrite-java-25`'s classes
into our own `META-INF/versions/25` at package time. It keeps consumers'
classpaths pristine but duplicates/shades a third-party artifact, doubles the
packaging logic, and fights the CLI shade step. The plain dependency plus the
MR bridge is simpler and the version-69 classes on the classpath are provably
inert (verified by T11 smoke tests).

### 3.6 CLI fat jar

`maven-shade-plugin` flattens everything; JEP 238 semantics survive only if
the shaded manifest carries `Multi-Release: true` and the
`META-INF/versions/25/**` entries are preserved (they are ordinary
distinct-path resources — no collisions with base entries). Add the manifest
entry to the existing `ManifestResourceTransformer` configuration and verify
by integration test (T10).

## 4. TDD Execution Phases

Each phase ends green on both JDKs before the next begins. Run commands:

```
mvn clean install                                 # full reactor, current JDK
mvn clean test -pl codebase-graph-builder         # module under change
mvn spotless:apply                                # formatting
```

### Phase 1 — RED: contract tests for the new seam

Write `Java25ParserFactoryTest`
(`codebase-graph-builder/src/test/java/org/hjug/graphbuilder/graphbuilder/`).
All environment-sensitive tests follow the existing pattern of
`@EnabledOnJre(JRE.JAVA_25)` / `Runtime.version().feature()` guards.

- **T1** `returnsEmptyOnPre25Runtime` — skipped on JDK 25; asserts
  `Java25ParserFactory.createJava25Parser()` is empty. *(Fails: class does not
  exist → compile error = red.)*
- **T2** `versionedVariantCreatesWorkingJava25Parser` —
  `@EnabledForJreRange(min = JRE.JAVA_25)`; loads the Java 25
  `Java25ParserFactory` class from the versioned output in isolation, then
  reflectively invokes `createJava25Parser()`. Asserts that the resulting
  parser handles the flexible-constructor-body fixture, proving the versioned
  variant creates a working Java 25 parser. Packaged-JAR shadowing is covered
  separately by `MultiReleaseJarIT`; direct factory invocation belongs to T1.
- **T3** `baseAndVersionedVariantsExposeIdenticalPublicApi` —
  `@EnabledOnJre(JRE.JAVA_25)` (only meaningful when the versioned class file
  exists on disk): loads
  `target/classes/META-INF/versions/25/.../Java25ParserFactory.class` via an
  isolated child `URLClassLoader` that also has `rewrite-java-25` on it,
  reflectively compares public method signatures against
  `Java25ParserFactory.class`, asserts equality. Guards the JEP 238 API-parity
  rule (§3.2).

### Phase 2 — GREEN (base variant only)

Implement the base `Java25ParserFactory` in `src/main/java` (returns
`Optional.empty()`; `@Slf4j` unused here — keep the base variant dependency-
free and silent).

- T1 now passes on JDK 17/21. T2/T3 remain **red on JDK 25** — expected; do
  not skip them.

### Phase 3 — GREEN on JDK 25: versioned variant + MR packaging

1. `codebase-graph-builder/pom.xml`: add `jar` manifest
   `Multi-Release: true`; add `jdk25+` profile with the second compiler
   execution (§3.4).
2. Add the versioned `Java25ParserFactory` in `src/main/java25` referencing
   `Java25Parser` directly, with `catch (Throwable)` → `Optional.empty()` +
   `log.warn` fallback.
3. **T4** `packagedJarIsMultiRelease` (failsafe IT, `*IT.java`, JDK 25
   profile): opens `target/codebase-graph-builder-<v>.jar`, asserts manifest
   `Multi-Release: true` and presence of
   `META-INF/versions/25/org/hjug/graphbuilder/graphbuilder/Java25ParserFactory.class`.
4. Verify T2, T3, T4 green on JDK 25; T1 still green on JDK 17 (and T4 must be
   written to be absent-tolerant: on a JDK 17 build the profile is off, so the
   IT is skipped via a JUnit assumption that the versions dir exists).

### Phase 4 — Switch parser selection, retire reflection machinery

TDD: first update the existing behavioral tests, watch them fail, then refactor.

1. Rewrite `JavaSourceFileGraphBuilderJava25Test` from "force flag plumbing"
   to "factory-driven selection": on JDK 25 assert
   `createJavaParser(config)` returns a parser that accepts the module-import
   fixture; on 17/21 assert the standard parser is returned (e.g. parsing the
   fixture throws / graph build of pre-25 fixture succeeds). *(Red against the
   old implementation.)*
2. Implement the new `createJavaParser` body (§3.1). Green.
3. **T5** migration grep-guard (enforced by CI, not JUnit): zero matches for
   `tryCreateJava25Parser` in `src/main`.
4. Delete `Java25ParserWrapper` + `Java25ParserWrapperTest`.
5. `JavaRuntimeDetector` is used by `Java25AnalysisIntegrationTest` (assertion
   guard) and the removed log line only. Replace the integration-test guard
   with `@EnabledOnJre(JRE.JAVA_25)` (or `Runtime.version().feature() >= 25`),
   drop the debug-log line in `JavaSourceFileGraphBuilder`, then delete
   `JavaRuntimeDetector` + `JavaRuntimeDetectorTest`. *(Deciding: delete rather
   than keep-on-spec — re-adding a 45-line utility is trivial if ever needed.)*
6. Run `Java25AnalysisIntegrationTest` on both JDKs: JDK 25 leg must exercise
   the versioned factory end-to-end.

### Phase 5 — Remove all `forceJava25` flags, bottom-up (TDD via compiler)

Order chosen so each step compiles independently; the *existing full module
test suites are the regression net* — after each step run the module's tests.
Where a test exists solely for the flag, delete it in the same commit as the
flag.

1. **codebase-graph-builder**
   - `GraphBuilderConfig`: remove `forceJava25Parser` field + Javadoc.
   - `GraphBuilderConfigTest`: delete `forceJava25Parser_defaultsToFalse` and
     `forceJava25Parser_canBeSetViaBuilder`.
   - `mvn clean test -pl codebase-graph-builder` → green.
2. **cost-benefit-calculator**
   - `CycleRanker`: delete the 3-arg
     `generateClassReferencesGraph(boolean, String, boolean)`; the 2-arg
     method becomes the only overload and builds `GraphBuilderConfig` without
     the flag.
   - Delete `CycleRankerForceJava25ParserTest` in the same commit.
   - **T6** add/keep a `CycleRanker` test asserting the 2-arg overload builds
     the graph (the surviving behavior the deleted test inadvertently covered).
   - `mvn clean test -pl cost-benefit-calculator` → green.
3. **report**
   - `SimpleHtmlReport`: remove the trailing `boolean forceJava25Parser`
     parameter from `execute(...)` and `generateReport(...)`, and the argument
     at both `generateClassReferencesGraph` call sites; drop the Javadoc lines.
   - `JsonGenerator`: same for `execute(...)` and `generateReportData(...)`.
   - `HtmlReport`: remove the trailing parameter from `generateReport(...)`
     (reached transitively via `RefactorFirstMavenReport`).
   - `JsonGeneratorTest`: update the force-overload test (around the
     `forceJava25Parser` overload section) to call the new signature and
     assert the same report-data behavior.
   - `mvn clean test -pl report` → green.
4. **refactor-first-maven-plugin**
   - Remove the `@Parameter(property = "forceJava25Parser")` field (and
     Javadoc) from `RefactorFirstMavenReport`, `RefactorFirstHtmlReport`,
     `RefactorFirstSimpleHtmlReport`, `RefactorFirstMavenJsonGenerator`, and
     the trailing argument at each call into the report classes.
   - **T7** built-in regression: `mvn clean test -pl refactor-first-maven-plugin`
     regenerates the plugin descriptor/helpmojo; assert (manual or scripted)
     the generated `plugin.xml`/help goal no longer mentions
     `forceJava25Parser`.
5. **Final repo-wide sweep (T8)**: `grep -ri "forcejava25" .` returns only
   historical plan documents (`plans/add-java-25-analysis.md`, this file) before
   moving on.

### Phase 6 — Ship it: dependency distribution + CLI fat jar + release pipeline

1. `codebase-graph-builder/pom.xml`: remove `<optional>true</optional>` from
   `rewrite-java-25` and update the comment to describe the MR-JAR rationale.
   - **T9** `mvn -pl refactor-first-maven-plugin dependency:tree | grep
     rewrite-java-25` → now present (inverted regression check vs. today's
     state).
2. `cli/pom.xml`: add
   `<manifestEntries><Multi-Release>true</Multi-Release></manifestEntries>` to
   the shade `ManifestResourceTransformer`.
3. **T10** CLI packaging IT (script or invoker-style check):
   `unzip -l cli/target/cli-<v>.jar` shows
   `META-INF/versions/25/org/hjug/graphbuilder/graphbuilder/Java25ParserFactory.class`,
   and `unzip -p … META-INF/MANIFEST.MF` contains `Multi-Release: true`.
4. **T11** runtime smoke matrix (CI jobs, both against the fat jar and a
   `mvn …:htmlReport` invocation on a fixture project):
   - JDK 17: runs to completion; log contains standard-parser message; no
     `UnsupportedClassVersionError`/`NoClassDefFoundError` anywhere.
   - JDK 25: log contains "Using Java 25 parser" (move that `log.info` into
     the versioned factory success path) and the module-import fixture is
     graphed.
5. `.github/workflows/release.yml`: bump `java-version: '17'` → `'25'` so
   published artifacts contain the versioned entries.
   - **T12** release guard: add a step (or enforcer rule) failing the release
     build if `META-INF/versions/25/.../Java25ParserFactory.class` is absent
     from `codebase-graph-builder/target/*.jar`.
6. Re-check `cli` fat-jar size (`du -sh`) and record the new figure in
   AGENTS.md (it will grow by the size of `rewrite-java-25`, which was
   previously absent).

### Phase 7 — Documentation updates (same PR)

- `README.md`: delete the `forceJava25Parser` row from the configuration table;
  if a Java 25 section exists, update it to "automatic on JDK 25+ runtimes".
- `AGENTS.md`: rewrite the "Java 25 analysis" section — describe the MR-JAR
  design, the `jdk25+` build profile, the JDK-25 release-build requirement, the
  no-longer-optional `rewrite-java-25` dependency, and remove `-DforceJava25Parser`
  from the mojo option list. (Required by project convention.)
- `CLAUDE.md`: replace the reflection/optional paragraph (line ~101) with the
  MR-JAR description mirroring AGENTS.md.
- Leave `plans/add-java-25-analysis.md` untouched (historical record); this
  plan supersedes its approach.

## 5. Complete File Change List

**codebase-graph-builder**
- `pom.xml` — MR manifest, `jdk25+` profile, drop `<optional>`
- `…/graphbuilder/Java25ParserFactory.java` — **new** (base)
- `src/main/java25/org/hjug/graphbuilder/graphbuilder/Java25ParserFactory.java` — **new** (versioned)
- `…/graphbuilder/JavaSourceFileGraphBuilder.java` — new `createJavaParser` body
- `…/GraphBuilderConfig.java` — remove field
- `…/JavaRuntimeDetector.java` — **delete**
- `…/graphbuilder/Java25ParserWrapper.java` — **delete**
- Tests: new `Java25ParserFactoryTest` (+ `Java25ParserFactoryPackagingIT`);
  rewrite `JavaSourceFileGraphBuilderJava25Test`; trim `GraphBuilderConfigTest`;
  delete `Java25ParserWrapperTest`, `JavaRuntimeDetectorTest`;
  update `Java25AnalysisIntegrationTest` guard

**cost-benefit-calculator**
- `CycleRanker.java` — drop 3-arg overload; delete
  `CycleRankerForceJava25ParserTest`, keep/adjust surviving-overload test (T6)

**report**
- `SimpleHtmlReport.java`, `JsonGenerator.java`, `HtmlReport.java` — remove
  parameter; `JsonGeneratorTest` — update overload test

**refactor-first-maven-plugin**
- 4 mojos — remove `@Parameter` field and call argument

**cli**
- `pom.xml` — shade `Multi-Release: true` manifest entry

**build/docs**
- `.github/workflows/release.yml` — JDK 25 + presence guard
- `README.md`, `AGENTS.md`, `CLAUDE.md`

## 6. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| JDK 17 build silently produces a JAR without versioned entries | Profile is explicit; T4 IT documents expected layout; T12 fails the *release* build if entries are missing; release.yml pinned to JDK 25. |
| No automatic JEP 238 API-parity validation (we don't use `jar --release`) | T3 reflective signature comparison; class kept to one public method. |
| Shade plugin mangling MR entries in fat jar | T10 IT asserts manifest + entries in the built artifact every build. |
| `rewrite-java-25` (v69 classes) now on JDK 17 plugin/CLI classpaths | Provably inert — only reachable through the versioned factory, which JDK 17 class loaders cannot see; T11 JDK 17 smoke run proves no linkage errors. Defensive `catch (Throwable)` retained. |
| Maven < 3.x / ancient classloaders ignoring MR manifests | Supported runtimes are JDK 17+; `ClassRealm` extends `URLClassLoader`, which honors MR JARs since JDK 9. T11's JDK 25 plugin run verifies activation in the real realm. |
| Removed `-DforceJava25Parser` breaks a user's build script | Flag was inert-by-design (could only ever fail gracefully on <25); note the removal in the changelog/release notes as a **breaking change** for the next minor release. |
| `CostBenefitCalculatorTest.testCostBenefitCalculation` stays disabled on JRE 25 (upstream OpenRewrite bug) | **Resolved during implementation:** with `rewrite-java-25` now always on the classpath, the formerly disabled test passes on JDK 25 (`@DisabledOnJre` removed). The upstream issue remains open but no longer reproduces for this fixture. |

## Implementation Notes (post-implementation, 2026-09-22)

Deviations from the plan discovered during implementation:

1. **OpenRewrite's `JavaParser.fromJavaVersion()` already elevates to
   `Java25Parser` on JDK 25** when `rewrite-java-25` is on the classpath
   (via `JdkParserBuilderCache`'s internal reflection). With §3.5's
   distribution change this guarantees a Java 25-capable parser on JDK 25 even
   from exploded classpaths, so `JavaSourceFileGraphBuilderJava25Test` and
   `Java25AnalysisIntegrationTest` assert a 25-capable parser on JDK 25
   directly rather than skipping on the factory's base variant. The factory
   remains the deterministic selection mechanism for the packaged
   (jar-loaded) path, verified by `MultiReleaseJarIT`.
2. **`rewrite-java-25` jars declare no `META-INF/services` entries**, which is
   what makes shipping them on Java 17/21 classpaths safe (no ServiceLoader
   linkage risk), independent of the MR-JAR bridge.
3. **Unplanned dependency bump:** `maven-plugin-plugin` /
   `maven-plugin-annotations` 3.15.1 → **3.16.0** in
   `refactor-first-maven-plugin`. Descriptor generation scans dependency
   archives with ASM and the 3.15.1-bundled ASM cannot read class-file 69;
   3.16.0 ships ASM 9.10.1.
4. **JaCoCo** gained `<exclude>META-INF/versions/**</exclude>` in the parent
   pom — its report goal rejects duplicate class names ("Can't add different
   class with same name").
5. `Multi-Release: true` is declared unconditionally (not just in the JDK 25
   profile); it is legal and inert in a jar without versioned entries.
6. The `cli` module is commented out of the reactor (`pom.xml`); the fat jar
   was built standalone with `mvn -f cli/pom.xml clean install` for the
   T10/T11 verification.
7. Pre-existing, **out of scope but noted**: the CLI entry point crashes in
   this checkout because `ReportCommand` declares `--output` on both
   `testSourceDirectory` and `outputDirectory` (picocli
   `DuplicateOptionAnnotationsException`). Committed code, unrelated to this
   change; the T11 smoke tests therefore drove `SimpleHtmlReport` directly
   against the shaded fat jar instead of the picocli entry point.

## 7. Definition of Done

1. `mvn clean install` green on JDK 17 **and** JDK 25 (CI matrix).
2. `grep -ri "forcejava25" --include="*.java" .` → zero matches.
3. On JDK 25: plugin and CLI runs log Java 25 parser usage and parse the
   module-import fixture; on JDK 17: both run with the standard parser and no
   linkage errors.
4. Packaged `codebase-graph-builder` JAR and CLI fat jar both contain
   `META-INF/versions/25/.../Java25ParserFactory.class` and
   `Multi-Release: true`; `refactor-first-maven-plugin` dependency tree includes
   `rewrite-java-25`.
5. Release workflow builds on JDK 25 and fails if the versioned entries are
   missing.
6. README / AGENTS.md / CLAUDE.md describe the MR-JAR mechanism and no longer
   mention the flag.
