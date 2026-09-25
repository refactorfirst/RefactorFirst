# Implementation Plan: Finish the RefactorFirst Gradle Plugin (PR 157)

## 1. Goal

Complete the Gradle plugin started in
[PR #157](https://github.com/refactorfirst/RefactorFirst/pull/157) so that it is correct, tested, and publishable.

### Contract (acceptance criteria)

| Requirement                     | Value                                                                                                                                                                                   |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Plugin id                       | `org.hjug.refactorfirst`                                                                                                                                                                |
| Tasks                           | `refactorFirstHtmlReport`, `refactorFirstSimpleHtmlReport`, `refactorFirstCsvReport`, `refactorFirstJsonReport`                                                                         |
| HTML / Simple HTML / CSV output | `<projectDir>/build/reports/refactorfirst/` of the project the task is invoked on                                                                                                       |
| JSON output                     | `<projectDir>/.refactorfirst/`                                                                                                                                                          |
| JSON implementation             | `refactorFirstJsonReport` **must invoke `org.hjug.refactorfirst.report.JsonGenerator`** (the Maven plugin's generator), **not** `org.hjug.refactorfirst.report.json.JsonReportExecutor` |
| Up-to-date/caching behavior     | Tasks **always run** when invoked (never `UP-TO-DATE`, never `FROM-CACHE`)                                                                                                              |

Development is TDD: every phase lands a failing test first, then the implementation that turns it green. Commands assume
the plugin's own Gradle wrapper: `cd refactor-first-gradle-plugin && ./gradlew …`.

## 2. Current state (what PR 157 actually delivered)

`../refactor-first-gradle-plugin` (excluded from the Maven reactor; built by its own wrapper, Gradle 9.0.0):

- `RefactorFirstPlugin` — creates extension `refactorFirst`, registers the 4 correctly-named tasks (group
  `RefactorFirst`).
- `RefactorFirstExtension` — mirrors Maven plugin options (`showDetails`, `backEdgeAnalysisCount`, `analyzeCycles`,
  `minifyHtml`,
  `excludeTests`, `testSourceDirectory`, `projectName`, `projectVersion`,
  `outputDirectory`).
- `HtmlReportTask`, `SimpleHtmlReportTask`, `CsvReportTask`, `JsonReportTask`
  — each pulls the extension in `@TaskAction generate()` via `getProject()`
  and calls the report module (`HtmlReport`/`SimpleHtmlReport` 10-arg
  `execute`, `CsvReport.execute`, `JsonReportExecutor.execute`).

### Gaps vs. the contract

1. **Plugin id is `org.hjug.refactor-first`**, not `org.hjug.refactorfirst`.
2. **Default output is `target/site` via `RefactorFirstExtension.
   resolveOutputDir`** — not `build/reports/refactorfirst` / `.refactorfirst`.
3. **`RefactorFirstPlugin.relativizeToProject()` converts the output dir into a project-relative string**, which the
   report layer then resolves against the *process working directory* (a Devin review finding on the PR). In a Gradle
   daemon this misplaces or escapes reports. The current report layer accepts absolute paths, so the helper must be
   deleted, not fixed.
4. **`JsonReportTask` must invoke `JsonGenerator`, not `JsonReportExecutor`.**
   PR 157 wired the task to `JsonReportExecutor`, which writes
   `refactor-first-data.json` into an arbitrary directory and leaks a
   `CostBenefitCalculator`/JGit handle per run (a Devin review finding, poisonous in a long-lived Gradle daemon). The
   task must instead call
   `org.hjug.refactorfirst.report.JsonGenerator` — the same class the Maven plugin's `jsonReport` mojo uses — which
   already writes exactly
   `<baseDir>/.refactorfirst/refactor-first.json` plus the bundled viewer when passed `outputDir == null`.
   `JsonReportTask` must not reference
   `JsonReportExecutor` after this change (verify by grep / code review, and by T7's assertion set).
5. **No tests at all.**
6. **Build plumbing is stale:** hardcoded `version = '0.7.2-SNAPSHOT'` and
   `implementation 'org.hjug.refactorfirst.report:report:0.7.2-SNAPSHOT'`
   (main line is `0.11.0-SNAPSHOT`); toolchain is Java 11 (project baseline is 17; Gradle 9 requires a 17+ JVM anyway);
   a `maven { url 'target/dependencies' }`
   repo hack with a 400+ file local cache dump sitting in `target/dependencies/`;
   `shadowJar { minimizeJar = true }` (which would strip `META-INF/versions/25`
   and break JEP 238 Java 25 parsing — and caused the PR author's unexplained runtime errors); `gradlew.bat` still
   passes an empty `-classpath ""` next to
   `-jar` (CodeRabbit finding on the PR).

## 3. Design decisions (made now so phases stay mechanical)

1. **Publish a thin jar.** `java-gradle-plugin` + `maven-publish` (+ optional
   `com.gradle.plugin-publish`) produce normal jars with a POM; Gradle resolves `report` and its transitives as real
   jars. This removes the need for `shadowJar`/`minimizeJar` entirely (deleting both), and preserves the JEP 238
   multi-release activation of the Java 25 parser for free: plugin classloaders load `codebase-graph-builder-*.jar`
   (with its
   `Multi-Release: true` manifest) as a real jar from the dependency cache. **Do not shade.** Keep
   `apply plugin: 'com.gradleup.shadow'` removed.
2. **Absolute output paths, no relativization.** Each task computes an absolute `File` for its output directory at
   configuration time and passes its absolute path to the existing report APIs (they accept absolute path strings;
   `ReportWriter.writeReportToDisk` normalizes via
   `Path.of(...).toAbsolutePath()`). Delete `relativizeToProject`.
3. **Defaults:** HTML/SimpleHTML/CSV default output is
   `layout.buildDirectory.dir("reports/refactorfirst")`; JSON has no configurable output and always goes to
   `<projectDir>/.refactorfirst` via
   `JsonGenerator.execute(..., baseDir, null)`. Extension `outputDirectory`
   (when set) overrides the HTML/SimpleHTML/CSV default only.
4. **Always run:** tasks declare no `@Input`/`@Output` (so Gradle has nothing to compare) **and** each task constructor
   calls
   `getOutputs().upToDateWhen(t -> false)` as an explicit guard; none of the tasks are annotated `@CacheableTask`.
5. **Configuration-cache / Gradle 9 hygiene:** no `getProject()` inside
   `@TaskAction`. Values are captured at registration time into task fields (extension reference resolved in `apply`,
   `projectDir` via
   `project.getLayout().getProjectDirectory()`). The extension object is read eagerly at execution (users set it in
   `build.gradle(.kts)` before execution; acceptable because no `@Input`s are declared — see 4).
6. **Version sync with Maven:** `gradle.properties` carries
   `refactorFirstVersion=0.11.0-SNAPSHOT`, used for both the plugin's version and the `report` dependency coordinate. A
   CI check greps it against the Maven `project.version` to prevent drift (release builds consume the published
   non-SNAPSHOT `report` from Maven Central; local dev uses
   `mavenLocal()` after `mvn install`).

## 4. Phases

Per-phase rule: write the failing test (s), watch them fail for the stated reason, then implement and re-run. Run
`./gradlew test` (and once wired, the functional suite) after every phase.

### Phase 0 — Repo hygiene & build scaffolding (no behavior change)

Infra that makes later TDD possible; commit separately.

- `build.gradle`:
    - `version` ← `gradle.properties` `refactorFirstVersion`; dependency →
      `implementation "org.hjug.refactorfirst.report:report:$refactorFirstVersion"`.
    - Toolchain `11` → `17`.
    - Remove `repositories { maven { url 'target/dependencies' } }`; keep
      `mavenLocal()` + `mavenCentral()`.
    - Remove the `com.gradleup.shadow` plugin, the `shadowJar { minimizeJar }`
      block, and the bogus `jar` manifest `Main-Class` attribute (a plugin jar has no main class). Add
      `dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.13.3';
      testImplementation 'org.eclipse.jgit:org.eclipse.jgit:7.7.0.202606012155-r';
      testImplementation gradleTestKit() }` and
      `tasks.test { useJUnitPlatform() }`.
    - Keep `gradlePlugin {}` block; **do not change the id yet** (Phase 1).
- Delete the stale on-disk junk: `../refactor-first-gradle-plugin/target`,
  `build/`, `.gradle/` (untracked); ensure root/module `../.gitignore` cover
  `.gradle/` and `build/`.
- `gradlew.bat`: drop the empty `-classpath "%CLASSPATH%"` from the
  `-jar` invocation line (PR 157 CodeRabbit finding).
- Sanity: `./gradlew help` works on JDK 17 and JDK 25.

### Phase 1 — Plugin id & task registration (unit tests via `ProjectBuilder`)

New test: `src/test/java/org/hjug/gradlereport/RefactorFirstPluginTest.java`
(`ProjectBuilder.builder().build()` +
`project.getPluginManager().apply("org.hjug.refactorfirst")`).

RED tests:

- T1 `appliesByContractId` — applying `org.hjug.refactorfirst` succeeds. (Fails today: id is `org.hjug.refactor-first`.)
- T2 `registersAllFourTasksWithContractNames` — after apply,
  `tasks.getByName(...)` resolves each of the 4 contract names with group
  `RefactorFirst` and a non-blank description.
- T3 `tasksAreNeverUpToDate` — for each task,
  `!task.getOutputs().getUpToDateSpec().isSatisfiedBy(task)` and no declared inputs/outputs
  (`task.getInputs().getHasInputs()` is false).

GREEN:

- `build.gradle` → `gradlePlugin { plugins { refactorFirstPlugin { id =
  'org.hjug.refactorfirst' ... } } }`.
- Each task constructor adds
  `getOutputs().upToDateWhen(t -> false);` (with a javadoc/comment stating the contract: reports always regenerate).

### Phase 2 — Output locations & task wiring (functional tests via TestKit)

New test: `RefactorFirstPluginFunctionalTest.java` (GradleRunner, JUnit 5 `@TempDir`).

Shared fixture helper in the test:
`newSampleProject(File dir)` — writes `settings.gradle.kts` and
`build.gradle.kts` (`plugins { id("org.hjug.refactorfirst") }`), creates
`src/main/java/com/example/Sample.java`, and uses JGit to `init`, add, and commit (the report layer early-returns
without a `.git` dir).

RED tests (fail on output location + id today):

- T4 `htmlReportWritesToBuildReportsRefactorFirst` — running
  `refactorFirstHtmlReport` succeeds and
  `build/reports/refactorfirst/refactor-first-report.html` exists (assert non-empty).
- T5 `simpleHtmlReportWritesToBuildReportsRefactorFirst` — same path and file name for `refactorFirstSimpleHtmlReport`.
- T6 `csvReportWritesToBuildReportsRefactorFirst` — running
  `refactorFirstCsvReport` creates a file matching
  `build/reports/refactorfirst/RefFirst_P*_PV*_PD*.csv`.
- T7 `jsonReportProducesJsonGeneratorOutput` — running
  `refactorFirstJsonReport` exercises the `JsonGenerator` code path: it creates `../.refactorfirst/refactor-first.json`
  **plus** the bundled viewer (`index.html`, `refactor-first-report.mustache` — resources only
  `JsonGenerator` copies), the JSON parses as
  `RefactorFirstReportDTO` (not the legacy `JsonReport` DTO), and
  `refactor-first-data.json` (**only** produced by the former
  `JsonReportExecutor` wiring) is **not** created anywhere.
- T8 `noReportsAtLegacyLocations` — none of `target/site`,
  `refactor-first-data.json`, or a top-level `reports/` dir are created by any of the 4 tasks.

GREEN (implement after all five fail):

- `RefactorFirstExtension`: default output becomes
  `build/reports/refactorfirst`; `resolveOutputDir(File)` is removed.
- Plugin `apply()`: capture `DirectoryProperty reportsDir =
  project.getLayout().getBuildDirectory().dir("reports/refactorfirst")` and the extension; pass into each task at
  registration (task constructor or setters)
  — no `getProject()` inside `@TaskAction`.
- `HtmlReportTask` / `SimpleHtmlReportTask`:
  `report.execute(backEdgeAnalysisCount, analyzeCycles, showDetails,
  minifyHtml, excludeTests, testSourceDirectory, projectName, projectVersion,
  projectDirFile, outputDirFile.getAbsolutePath())`.
- `CsvReportTask`: `csvReport.execute(showDetails, projectName, projectVersion,
  outputDir.getAbsolutePath(), projectDirFile)`.
- `JsonReportTask`: **replace `JsonReportExecutor` with `JsonGenerator`**:
  `new JsonGenerator().execute(backEdgeAnalysisCount, analyzeCycles,
  showDetails, excludeTests, testSourceDirectory, projectName, projectVersion,
  projectDirFile, null)` (null outputDir ⇒ `<projectDir>/.refactorfirst`). Remove the
  `org.hjug.refactorfirst.report.json.JsonReportExecutor` import entirely. This also eliminates the daemon-hostile JGit
  handle leak flagged on the PR.
- Delete `RefactorFirstPlugin.relativizeToProject`.

Notes:

- `HtmlReport` extends `SimpleHtmlReport`; both write
  `refactor-first-report.html`.
- Functional tests must invoke the tasks with `--quiet` and no
  `--configuration-cache` flags initially; add a single config-cache run (`--configuration-cache`) at the end asserting
  "no problems were found" to guard the no-`getProject()`-at-execution rule.

### Phase 3 — Always-run contract (functional)

RED → GREEN (these pin the contract; they may already pass after Phase 1, in which case they are characterization
tests — still required):

- T9 `tasksRunEveryTime`: for each of the 4 tasks, run it twice (two
  `GradleRunner` builds over the same project), assert both outcomes are
  `TaskOutcome.SUCCESS` (not `UP_TO_DATE` / `FROM_CACHE`).
- T10 `outputsChangeWhenSourceChanges`: run `refactorFirstJsonReport` twice with a source-file edit + new commit between
  runs; assert
  `../.refactorfirst/refactor-first.json` mtime/content changes (guards against accidental caching introduced later).

If T9/T10 fail, the fix lives in the task classes (see Phase 1 step for
`upToDateWhen`), not in test flags.

### Phase 4 — Extension mapping (unit + functional)

- T11 `extensionDefaultsMatchContract` (unit): `showDetails=false`,
  `backEdgeAnalysisCount=50`, `analyzeCycles=true`, `minifyHtml=false`,
  `excludeTests=true`, blank `testSourceDirectory`/`projectName`/
  `projectVersion`, no `outputDirectory` override.
- T12 `extensionOverridesAreHonored` (functional): set
  `refactorFirst { outputDirectory = "build/rf"; projectName = "CustomName" }`
  in the sample project, run `refactorFirstHtmlReport`, assert output lands in
  `build/rf/` and the HTML contains `CustomName`.

GREEN: route extension values through the (already wired) registration-time capture implemented in Phase 2.

### Phase 5 — Publishing, CI, docs

- `com.gradle.plugin-publish` (latest 2.x line) configured with
  `website`/`vcsUrl`/tags; `publishPlugins` remains a maintainer-only manual step gated on portal credentials.
  Maven-publish remains for Maven Central consumers using `classpath(...)` in `buildscript`.
- CI: new job in `maven.yml` / `maven-pr.yml` (or a dedicated
  `gradle.yml`) running `cd refactor-first-gradle-plugin && ./gradlew clean
  build` on the JDK 17 + 25 matrix; add the version-sync check (grep `gradle.properties` vs Maven `project.version`).
- `../README.md`: replace the "But I'm using Gradle / dummy POM" section with real instructions
  (`plugins { id("org.hjug.refactorfirst") version "…" }`, task list, output locations, extension reference table).
- `../AGENTS.md`: add a short "Gradle plugin" section (module path, build/test commands, thin-jar-no-shadow rule, contract
  table pointer) — required by repo convention.
- Release workflow: note that a release must publish `report` et al. to Maven Central **before** the plugin version can
  be consumed (thin-jar runtime dependency), and that the `gradle.properties` version is bumped in the same commit that
  sets the next development version.

### Phase 6 — Final verification

- `./gradlew clean build` green on JDK 17 **and** JDK 25 (Java 25 analysis works through the real multi-release
  `codebase-graph-builder` jar; no gradle-side work needed — assert the functional tests pass on 25).
- Windows + Linux (and macOS if available) CI legs.
- Full `mvn clean install` still green from the repo root (nothing in the Maven reactor may change; Phase 2/5 touch only
  `refactor-first-gradle-plugin`, docs, and workflows).

## 5. Explicitly out of scope (with reason)

- **Fixing `JsonReportExecutor`'s `CostBenefitCalculator` leak** — the Gradle plugin stops using it (Phase 2). The CLI
  is a short-lived process where the leak is harmless. If desired, a separate tiny PR can wrap it in try-with-resources.
- **Shading / fat-jar distribution of the plugin** — replaced by thin-jar publishing (§3.1). This also retires the PR
  TODO "figure out why the jar is so large" and eliminates the `minimizeJar` risk of stripping
  `META-INF/versions/25` (Java 25 parser).
- **The Devin "subproject reports reject valid repositories" finding** — it targeted the report layer as it existed in
  the PR; since then the report layer gained multi-module/repository-root support. Re-evaluate only if a Phase 2
  functional test on a multi-project sample reveals it again (otherwise drop).
- **Per-subproject task registration / aggregating multi-module reports** — the contract is "the project where it is
  invoked"; users apply the plugin per project.

## 6. Definition of Done

1. `build.gradle` declares plugin id `org.hjug.refactorfirst`; T1–T3 prove id, names, and never-up-to-date via
   `ProjectBuilder`.
2. TestKit functional tests T4–T10 green on JDK 17 and JDK 25: HTML & Simple HTML produce
   `build/reports/refactorfirst/refactor-first-report.html`, CSV produces
   `build/reports/refactorfirst/RefFirst_P*_PV*_PD*.csv`, and
   `refactorFirstJsonReport` is proven to run `JsonGenerator` (not
   `JsonReportExecutor`): `../.refactorfirst/refactor-first.json` + viewer files are produced (T7). Every task reports
   `SUCCESS` on repeat execution.
3. No `target/dependencies` repo hack, no shadow/minimize config; the plugin jar is thin; `gradlew.bat` no longer passes
   an empty `-classpath`.
4. Docs updated (README + AGENTS.md); CI runs the Gradle build; version-sync check in place.
5. Maven reactor build remains green.
