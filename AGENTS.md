# RefactorFirst Agent Guide

## Essential Commands
- Full build: `mvn clean install`
- Skip tests: `mvn clean install -DskipTests`
- Run specific module tests: `mvn clean test -pl <module-name>`
- Run single test class: `mvn clean test -pl effort-ranker -Dtest=<TestClassName>`
- Format code: `mvn spotless:apply`
- Check formatting: `mvn spotless:check`
- Build with OWASP dependency check: `mvn clean install -Plocal`

## Key Architecture Points
- 11-module Maven build with data flowing left-to-right through the pipeline
- Central DTO: `CodebaseGraphDTO` (JGraphT graphs + disharmony lists + metrics)
- CLI entry point: `org.hjug.refactorfirst.Main` → `ReportCommand`
- Fat jar location: `cli/target/cli-<version>.jar`
- **Anonymous/synthetic classes are first-class graph members.** Java `Outer$N`/`Outer$` (anonymous/synthetic inner classes) and the Kotlin literal `"<anonymous>"` FQN are **not** sieved out by `GraphDependencyCollector`; they genuinely participate in cycles and can harbour antipatterns, so they are vertices in the class graph and rendered with `$` as the enclosing-class separator. `GraphDependencyCollector` keeps only the `from == to` self-edge guard, plus a degenerate-package guard so a packageless `"<anonymous>"` source never creates an `""` package-graph vertex. **Sink-only** anonymous/synthetic vertices (those with no outgoing edges) are suppressed only at render time in `HtmlReport.isSinkAnonymousOrSyntheticVertex` to keep the Class/Cycle Map DOT graph readable; active ones still render.
- **Anonymous DOT node ids are source-file derived.** OpenRewrite attributes a Kotlin anonymous object / function-literal type with {@code "<anonymous>"} as the trailing simple-name segment of its FQN: standalone ({@code "<anonymous>"}) or, in real graphs (e.g. FXGL), prefixed by the enclosing class/package ({@code "dev.DeveloperWASDControl.<anonymous>"}). {@code HtmlReport.isAnonymousFqn(vertex)} detects a vertex when its trailing segment starts with {@code <}. {@code HtmlReport.renderSafeNodeId(vertex, codebaseGraphDTO)} then derives the enclosing owner from the vertex's mapped source-file path in {@code CodebaseGraphDTO.classToSourceFilePathMapping} (file base name without extension, e.g. {@code DeveloperWASDControl.kt} -> {@code DeveloperWASDControl}). The DOT node id renders as {@code DeveloperWASDControl_anonymous} and the human-readable label as {@code DeveloperWASDControl\$anonymous} ({@code $} escaped as {@code \$} for DOT). When no source path is mapped (or DTO is null) it degrades to the reversible {@code lt_}/{@code _gt} {@code <}/{@code >} encoding. The renderer is responsible for DOT/HTML-safe encoding of the literal {@code "<anonymous>"} FQN ({@code <}/{@code >} are illegal in Graphviz node ids; {@code <}/{@code >} escaping in HTML table labels).

## Java 25 analysis (JEP 238 multi-release jar)

`rewrite-java-25` is a **required** (BOM-managed, no explicit version) compile
dependency of `codebase-graph-builder` and ships in every distributed artifact
(Maven plugin, CLI fat jar). Its class files are Java 25 (class-file 69.0) but
are inert on Java 17/21 classpaths: the jar declares no `META-INF/services`
entries, and no base-level class in the module references it.

Activation uses a **JEP 238 multi-release jar**, not reflection or version
detection. `codebase-graph-builder` declares `Multi-Release: true` and ships
two variants of `org.hjug.graphbuilder.graphbuilder.Java25ParserFactory`:

- the base variant (`src/main/java`, `--release 17`) returns
  `Optional.empty()`;
- the Java 25 variant (`src/main/java25`, compiled `--release 25` into
  `META-INF/versions/25` by the auto-activated `jdk25-multi-release` profile)
  calls `Java25Parser.builder().build()` directly, wrapped in a
  `catch (Throwable)` graceful-degradation guard.

On JDK 25+ runtimes the JVM's versioned jar lookup shadows the base variant —
parser selection is fully runtime-driven with no configuration surface.
`JavaSourceFileGraphBuilder.createJavaParser(config)` is just
`Java25ParserFactory.createJava25Parser().orElseGet(...fromJavaVersion...)`.
Independently, OpenRewrite's `JavaParser.fromJavaVersion()` itself reflectively
elevates to `Java25Parser` on JDK 25+ whenever `rewrite-java-25` is on the
classpath, so exploded-directory classpaths (e.g. surefire against
`target/classes`, where JEP 238 shadowing does not apply) still get the
Java 25 parser on JDK 25. `Java25ParserFactoryTest` covers the factory
contract (including public-API parity of the two variants);
`MultiReleaseJarIT` verifies the packaged jar's manifest, versioned entries,
and real jar shadowing.

Constraints:

- **Never reference `rewrite-java-25` (or any class-file-69 code) from base
  sources** — only from `src/main/java25`.
- The versioned class must keep exactly the same public API as the base class.
- Release artifacts must be built on JDK 25+ (see `release.yml`, which also
  asserts the versioned entries exist); a build on JDK 17 succeeds but
  silently lacks `META-INF/versions/25`.
- JaCoCo excludes `META-INF/versions/**` (duplicate class names break its
  report goal); the CLI shade config re-adds `Multi-Release: true` to the fat
  jar manifest.
- `refactor-first-maven-plugin` uses `maven-plugin-plugin` /
  `maven-plugin-annotations` 3.16.0 because descriptor generation scans
  dependency archives with ASM and older versions cannot read class-file 69.

**JDK 25 build notes:**
- Spotless 3.10.2 with `palantir-java-format` 2.71.0 runs on JDK 17, 21 and
  25 with no `--add-exports` workaround (older Spotless 2.x /
  palantir-java-format builds failed on JDK 21+ without javac exports and
  could not run on JDK 25 at all).
- CI (`maven.yml`, `maven-pr.yml`) builds a JDK 17 + JDK 25 matrix; the Gradle
  plugin has its own workflow (`gradle.yml`) on the same JDK matrix plus
  Windows.
- `CostBenefitCalculatorTest.testCostBenefitCalculation` runs on all JDKs;
  its former `@DisabledOnJre(JRE.JAVA_25)` (OpenRewrite 8.90.4 fixture
  failure, issue #8712 family) no longer reproduces now that
  `rewrite-java-25` is always on the classpath — verified passing on JDK 25.

## Kotlin analysis (hard dependency)

`rewrite-kotlin` (`org.openrewrite:rewrite-kotlin`) is a **non-optional
compile dependency** of the `codebase-graph-builder` module, pulled in via that
module's `rewrite-bom` import (`rewrite-bom:8.90.4`). The Kotlin
parser is therefore always on the classpath of any consumer of
`codebase-graph-builder`; there is no opt-in and no reflective "is Kotlin
present?" guard. (An earlier, never-merged iteration made it `<optional>` with a
`CompositeGraphBuilder.isKotlinAvailable()` reflection guard, but the Kotlin
builder and visitors import `org.openrewrite.kotlin.*` directly and are
constructed via `new`, so the guard was dead code — it would have thrown
`NoClassDefFoundError` at `new KotlinSourceFileGraphBuilder()` before the guard
could ever run. The guard has been removed and the optionality dropped.)

**Distribution impact:** because the dependency is mandatory, the Maven plugin
and the CLI fat-jar bundle the Kotlin compiler —
`kotlin-compiler-embeddable:2.x` (verified at `2.3.20` in this build) and its
`kotlin-script-runtime` / `kotlin-daemon-embeddable` /
`kotlinx-coroutines-core-jvm` transitives — into **every** consumer's runtime,
including pure-Java projects that never contain a `.kt` file. As of this branch
the CLI fat-jar is `cli/target/cli-<version>.jar` and measures **~145 MB**
(verified via `du -sh cli/target/cli-0.11.0-SNAPSHOT.jar` after
`mvn clean install`); the Kotlin compiler and its transitives — and, since the
JEP 238 change, the non-optional `rewrite-java-25` — are a material fraction of
that. A pure-Java consumer therefore pays this size/cost (the dependency is
always on the classpath regardless).

**No opt-out:** Kotlin analysis runs unconditionally — there is no
`analyzeKotlin` switch on `GraphBuilderConfig`. The Kotlin parser is always
exercised. The config field `kotlinLanguageLevel` is kept as a `String` to
avoid importing `rewrite-kotlin`'s enum into the config DTO.

**Orchestration & fallback:** `CompositeGraphBuilder.getCodebaseGraphDTO(path,
config)` is the single orchestrator — it builds the Java graph, then the Kotlin
graph and merges them. A Kotlin build *failure* (parse error, IO, etc.) falls
back to returning the Java-only DTO with a `log.warn`
(`"Kotlin analysis failed; falling back to Java-only graph"`). This fallback
is for build failures, not for "Kotlin is absent".

## Gradle plugin

`refactor-first-gradle-plugin/` is **excluded from the Maven reactor** and built
by its own Gradle wrapper (Gradle 9.8.0 — 9.8+ required: the plugin classpath
carries the class-file-69 `rewrite-java-25` jar, whose bytecode Gradle ≤ 9.0
cannot instrument):

- Build & test: `cd refactor-first-gradle-plugin && ./gradlew clean build`
- Plugin id: `org.hjug.refactorfirst`; tasks `refactorFirstHtmlReport`,
  `refactorFirstSimpleHtmlReport`, `refactorFirstCsvReport`,
  `refactorFirstJsonReport` (task/output contract: see
  `plans/finish-pr-157-v2.md` §1; verified by `RefactorFirstPluginTest`
  [ProjectBuilder] and `RefactorFirstPluginFunctionalTest` [TestKit]).
- **Thin jar — no shadow plugin, no `minimizeJar`.** The plugin resolves
  `org.hjug.refactorfirst.report:report` and its transitives as real jars, so
  `codebase-graph-builder`'s JEP 238 `META-INF/versions/25` entries stay intact
  (shading with minimize would strip them and break Java 25 parsing).
- **No `getProject()` in `@TaskAction`** (Gradle 9 / configuration-cache
  hygiene); values are captured at registration time. Tasks declare no
  `@Input`/`@Output` and `getOutputs().upToDateWhen(t -> false)` — they always
  run (guarded by `configurationCacheRunHasNoProblems` and `tasksRunEveryTime`).
- `refactorFirstJsonReport` must invoke
  `org.hjug.refactorfirst.report.JsonGenerator` (the Maven plugin's generator),
  **never** `org.hjug.refactorfirst.report.json.JsonReportExecutor`.
- Version sync: `refactor-first-gradle-plugin/gradle.properties`
  `refactorFirstVersion` must equal the Maven `project.version`; `gradle.yml`
  fails the build on drift, and `release.yml` bumps both in the same commit.
  Release order: `report` et al. must be on Maven Central **before**
  `./gradlew publishPlugins` (maintainer-only) publishes the thin plugin jar.
- Local dev: run `mvn install` (or `-pl report -am`) first so `mavenLocal()`
  can resolve the SNAPSHOT `report` dependency, **then** `./gradlew
  publishToMavenLocal` so consuming builds can resolve the plugin marker
  `org.hjug.refactorfirst:org.hjug.refactorfirst.gradle.plugin` from
  `mavenLocal()`. Consumers need `mavenLocal()` in `pluginManagement
  repositories`; never point `resolutionStrategy.eachPlugin.useModule` at the
  marker coordinates (that module is POM-only) — either omit
  `resolutionStrategy` or point `useModule` at the implementation module
  `org.hjug.refactorfirst.plugin:refactor-first-gradle-plugin`.

## Testing Notes
- JUnit 5 with parameterized tests
- Test fixtures in `test-resources/src/test/resources`
- For graph algorithm changes, check `JavaGraphBuilderTest` and `CircularReferenceCheckerTests`
- Mutation testing via PIT available but not in default build

## Java & Toolchain
- Java 17 minimum (OpenRewrite supports 17, 21, 25)
- Lombok `@Data`/`@Builder` used extensively - avoid adding boilerplate it already removes
- SLF4J logging: `log.debug()` for verbose per-class output, `log.info()` sparingly
- Spotless enforces Palantir Java format

## Maven Plugin Usage
Generate reports directly:
`mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.9.0:htmlReport`

Configuration options (most important):
- `showDetails`: Shows God Class metrics in table (default: false)
- `backEdgeAnalysisCount`: 0 = analyze all back edges (default: 50)
- `analyzeCycles`: Whether to analyze cycles (default: true)
- `excludeTests`: Exclude test classes (default: true)
- `minifyHtml`: Minify HTML report (default: false)

(Java 25 parser activation is automatic on JDK 25+ runtimes via the JEP 238
multi-release jar — there is no longer any configuration flag for it.)

## CVE Pinning
Transitive dependencies surfaced by an OWASP dependency-check are pinned centrally in the
parent `pom.xml` `<dependencyManagement>` so child modules reference them by bare
GAV (no `<version>`). If a new transitive surfaces, add its fixed-version pin to the parent's
`<dependencyManagement>` block labelled "Centralized CVE mitigations",
recording the CVE ID, the NVD-quoted CVSS, and the affected range in the
comment, and drop the corresponding `<version>` from whichever child module introduced the
transitive. Currently pinned:
- `io.micrometer:micrometer-core:1.17.0` — CVE-2026-40984, CVSS 7.5, affected 1.9.0–1.9.17 / 1.13.0–1.13.18 / 1.14.0–1.14.15 / 1.15.0–1.15.11 / 1.16.0–1.16.5 (rewrite-core 8.86.0)
- `io.quarkus.gizmo:gizmo:1.9.0` — CVSS > 8.0 advisory in 1.0.11, no public CVE (rewrite-core)
- `org.apache.commons:commons-lang3:3.18.0` — CVE-2025-48924, CVSS 5.3, affected 3.0 before 3.18.0 (pmd-java, maven-reporting-impl)
- `org.iq80.snappy:snappy:0.5` — CVE-2024-36124, CVSS 5.3 (maven-core)
- `commons-beanutils:commons-beanutils:1.11.0` — CVE-2025-48734, CVSS 8.8 (maven-reporting-impl 4.0.0)

Note: `mvn clean install -Plocal` invokes the OWASP `dependency-check-maven`
plugin which requires NVD network access; in sandboxed / offline environments
the plugin emits HTTP 429 or `JdbcBatchUpdateException` and the build fails
on a network precondition rather than a code issue.
Re-verify any CVE ID quoted here against the NVD before bumping a pin; the citations were last verified on 2026-08-09.