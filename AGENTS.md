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
the CLI fat-jar is `cli/target/cli-<version>.jar` and measures **~144 MB**
(verified via `du -sh cli/target/cli-0.10.0-SNAPSHOT.jar` after
`mvn clean install -DskipTests`); the Kotlin compiler and its transitives are a
material fraction of that. A pure-Java consumer therefore pays this size/cost
(the dependency is always on the classpath regardless).

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