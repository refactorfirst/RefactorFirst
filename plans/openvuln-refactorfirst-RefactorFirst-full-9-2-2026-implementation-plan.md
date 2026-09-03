# OpenVuln RefactorFirst — Implementation Plan for All 13 Findings

This plan addresses all 13 security findings from the OpenVuln report using Test-Driven Development (TDD). Each finding is addressed with failing unit tests written first, then production code to make them pass.

---

## Finding 1: BUG-R2-S2-A1-H1 — HTML Report Origin-URL Embedding (XSS via `remote.origin.url`)

**Severity:** High (CVSS 7.4)  
**Root Cause:** `GitLogReader.getRepoUrl()` returns raw `.git/config` remote URL without validation/escaping; used in unquoted `href`, `javascript:` scheme, and DOT-in-script sinks.

### Files to Modify
- `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`
- `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`
- `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

### TDD Tasks

#### 1.1 Add tests for `GitLogReader.getRepoUrl()` validation
```java
// change-proneness-ranker/src/test/java/org/hjug/git/GitLogReaderTest.java

@Test void getRepoUrl_returnsEmpty_whenOriginUrlIsNull() throws IOException
@Test void getRepoUrl_returnsEmpty_whenOriginUrlIsNotHttpOrHttps() throws IOException
@Test void getRepoUrl_returnsEmpty_whenOriginUrlIsJavascriptScheme() throws IOException
@Test void getRepoUrl_returnsEmpty_whenOriginUrlIsDataScheme() throws IOException
@Test void getRepoUrl_returnsEmpty_whenOriginUrlIsFileScheme() throws IOException
@Test void getRepoUrl_sanitizesUrl_removingMarkupCharacters() throws IOException
@Test void getRepoUrl_allowsValidHttpsUrl() throws IOException
@Test void getRepoUrl_allowsValidHttpUrl() throws IOException
@Test void getRepoUrl_stripsGitSuffix() throws IOException
@Test void getRepoUrl_appendsBlobPath_forNonGithubHosts() throws IOException
```

#### 1.2 Add tests for HTML report URL escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/SimpleHtmlReportTest.java

@Test void printProjectHeader_escapesRepoUrlInHref() throws IOException
@Test void hyperlinkClass_escapesRepoUrlInHref() throws IOException
@Test void renderDisharmonyInfo_escapesRepoUrlInHref() throws IOException
```

```java
// report/src/test/java/org/hjug/refactorfirst/report/HtmlReportTest.java

@Test void hyperlinkClassForDot_escapesRepoUrlInDotUrlAttribute() throws IOException
@Test void generateGraphButtons_escapesRepoUrlInScriptTemplateLiteral() throws IOException
```

#### 1.3 Implement fix in `GitLogReader.getRepoUrl()`
- Add scheme allow-list (only `http://` and `https://`)
- Sanitize URL to RFC 3986 characters only
- Return empty string for invalid URLs

#### 1.4 Implement fix in report renderers
- Quote all `href` attributes
- Apply `escapeHtmlLabel` to all user-controlled strings in HTML context
- For DOT-in-script: escape `$`, `{`, `}`, backtick, `</script>`

---

## Finding 2: BUG-R2-S2-A1-H2 — POM Name/Version XSS in HTML Reports

**Severity:** High (CVSS 7.9)  
**Root Cause:** `projectName` and `projectVersion` from POM interpolated unescaped into `<h1>`, `<title>`, and no-disharmony `<div>`.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`
- `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

### TDD Tasks

#### 2.1 Add tests for POM value escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/SimpleHtmlReportTest.java

@Test void printProjectHeader_escapesProjectNameAndVersion() throws IOException
@Test void generateReport_escapesProjectNameInNoDisharmonyDiv() throws IOException
@Test void printTitle_escapesProjectNameAndVersion() throws IOException
```

```java
// report/src/test/java/org/hjug/refactorfirst/report/HtmlReportTest.java

@Test void printTitle_escapesProjectNameAndVersionInTitleTag() throws IOException
```

#### 2.2 Implement fix
- Apply `escapeHtmlLabel(projectName)` and `escapeHtmlLabel(projectVersion)` in:
  - `SimpleHtmlReport.printProjectHeader()` (line ~926)
  - `SimpleHtmlReport.generateReport()` no-disharmony branch (line ~364)
  - `HtmlReport.printTitle()` (line ~423)
- Enhance `escapeHtmlLabel` to also escape `"` and `'`

---

## Finding 3: BUG-R2-S2-A1-H3 — File Name/Path XSS in Class-Disharmony Tables

**Severity:** High (CVSS 7.4)  
**Root Cause:** File names and absolute paths from analyzed repo flow unescaped into `<a>` element body and `href` attribute, plus "Full Path" column.

### Files to Modify
- `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`
- `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`

### TDD Tasks

#### 3.1 Add tests for file name/path escaping
```java
// cost-benefit-calculator/src/test/java/org/hjug/cbc/CostBenefitCalculatorTest.java

@Test void getClassDisharmonies_usesUriPathForSourceFilePath() throws IOException
@Test void canonicaliseURIStringForRepoLookup_handlesRawAbsolutePath() throws IOException
```

```java
// report/src/test/java/org/hjug/refactorfirst/report/SimpleHtmlReportTest.java

@Test void renderDisharmonyInfo_escapesFileNameInAnchorBody() throws IOException
@Test void renderDisharmonyInfo_quotesAndEscapesHrefAttribute() throws IOException
@Test void renderDisharmonyInfo_escapesFullPathInShowDetailsMode() throws IOException
```

#### 3.2 Implement fix
- In `CostBenefitCalculator.getClassDisharmonies()`: use `Path.toUri().toString()` instead of raw path
- In `SimpleHtmlReport.renderDisharmonyInfo()`:
  - Quote `href` attribute: `href="..."`
  - Apply `escapeHtmlLabel()` to file name in anchor body
  - Apply `escapeHtmlLabel()` to full path in showDetails mode
- Enhance `escapeHtmlLabel` to escape `"` and `'`

---

## Finding 4: BUG-R2-S2-A1-H4 — Kotlin Class/Cycle/Method XSS in HTML Reports

**Severity:** High (CVSS 7.4)  
**Root Cause:** Kotlin backtick identifiers allow arbitrary characters; cycle names, method signatures, duplicate partners, package names rendered unescaped.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`
- `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

### TDD Tasks

#### 4.1 Add tests for Kotlin identifier escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/SimpleHtmlReportTest.java

@Test void getRankedCycleSummaryData_escapesCycleName() throws IOException
@Test void renderSingleCycle_escapesCycleNameInH2() throws IOException
@Test void renderDisharmonyInfo_escapesMethodSignature() throws IOException
@Test void renderDisharmonyInfo_escapesDuplicationPartners() throws IOException
```

```java
// report/src/test/java/org/hjug/refactorfirst/report/HtmlReportTest.java

@Test void renderPackageEdge_escapesPackageNames() throws IOException
@Test void renderPackageVertices_escapesPackageNames() throws IOException
@Test void renderClassVertices_escapesAnonymousLabels() throws IOException
```

#### 4.2 Implement fix
- Apply `escapeHtmlLabel()` to:
  - Cycle name in `getRankedCycleSummaryData()` (line ~776)
  - Cycle name in `renderSingleCycle()` (line ~793)
  - Method signature in `renderDisharmonyInfo()` (line ~1045)
  - Duplication partners in `renderDisharmonyInfo()` (line ~1068)
  - Package vertex names in `renderPackageEdge()` and `renderPackageVertices()`
- Enhance `escapeHtmlLabel` to escape `"` and `'`

---

## Finding 5: BUG-R2-S2-A2-H2 — DOT-in-Script XSS via `remote.origin.url`

**Severity:** High (CVSS 7.4)  
**Root Cause:** `remote.origin.url` embedded raw in JavaScript template literal inside `<script>` block for graph maps.

### Files to Modify
- `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java` (shared with Finding 1)
- `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

### TDD Tasks

#### 5.1 Add tests for DOT-in-script escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/HtmlReportTest.java

@Test void hyperlinkClassForDot_escapesRepoUrlForTemplateLiteral() throws IOException
@Test void buildClassGraphDot_escapesUrlInTemplateLiteral() throws IOException
@Test void buildClassCycleDot_escapesUrlInTemplateLiteral() throws IOException
@Test void buildPackageGraphDot_noUrlAttributeWhenNoSourceMapping() throws IOException
@Test void generateGraphButtons_scriptBlockExecutionSafe() throws IOException
```

#### 5.2 Implement fix
- Reuse `GitLogReader.getRepoUrl()` fix from Finding 1 (scheme allow-list + RFC 3986 sanitization)
- In `HtmlReport.hyperlinkClassForDot()`: escape `$`, `{`, `}`, backtick for template literal context
- In `buildClassGraphDot()`/`buildClassCycleDot()`: ensure template literal content is safe

---

## Finding 6: BUG-R2-S2-A2-H5 — Cycle Map Visuals XSS via Kotlin Class Names

**Severity:** High (CVSS 7.4)  
**Root Cause:** Cycle name (from Kotlin backtick class names) interpolated into unquoted HTML attributes, popup button bodies, and script blocks.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

### TDD Tasks

#### 6.1 Add tests for cycle map visual escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/HtmlReportTest.java

@Test void renderClassCycleVisuals_sanitizesCycleNameForJsIdentifier() throws IOException
@Test void renderClassCycleVisuals_sanitizesCycleNameForHtmlAttribute() throws IOException
@Test void renderClassCycleVisuals_sanitizesCycleNameForElementBody() throws IOException
@Test void generateGraphButtons_escapesCycleNameInConstDeclaration() throws IOException
@Test void generateDotImage_escapesCycleNameInDivIdAndModuleScript() throws IOException
@Test void generate2DPopup_escapesCycleNameInOnclickAndElementBody() throws IOException
@Test void generateForce3DPopup_escapesCycleNameInOnclickAndElementBody() throws IOException
@Test void generateHidePopup_escapesCycleNameInDivIds() throws IOException
```

#### 6.2 Implement fix
- In `renderClassCycleVisuals()`: sanitize cycle name to identifier-safe charset `[A-Za-z0-9_]`
- Apply sanitization before all 7 downstream positions:
  - JS identifier in `const <name>_dot`
  - HTML `id` attributes
  - JS string literals in `onclick`
  - Element body text in popup buttons
- Replace `$` → `_` and strip all non-identifier characters

---

## Finding 7: BUG-R2-S2-A5-H3 — Symlink Following in ReportWriter (Arbitrary File Overwrite)

**Severity:** High (CVSS 8.1)  
**Root Cause:** `ReportWriter.writeReportToDisk()` follows symlinks in output directory and output file path.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`

### TDD Tasks

#### 7.1 Add tests for symlink protection
```java
// report/src/test/java/org/hjug/refactorfirst/report/ReportWriterTest.java

@Test void writeReportToDisk_throwsWhenOutputDirIsSymlink() throws IOException
@Test void writeReportToDisk_throwsWhenOutputFileIsSymlink() throws IOException
@Test void writeReportToDisk_throwsWhenOutputDirIsDanglingSymlink() throws IOException
@Test void writeReportToDisk_throwsWhenOutputFileIsDanglingSymlink() throws IOException
@Test void writeReportToDisk_writesNormallyWhenNoSymlinks() throws IOException
@Test void writeReportToDisk_createsParentDirectories() throws IOException
```

#### 7.2 Implement fix
- Check `Files.isSymbolicLink(outputDir.toPath())` before `mkdirs()`
- Check `Files.isSymbolicLink(reportFile.toPath())` before `createNewFile()`
- Log error and return early (don't throw to avoid breaking existing callers)
- Use `Files.newBufferedWriter` with `LinkOption.NOFOLLOW_LINKS`

---

## Finding 8: BUG-R2-S2-A2-H1 — Bubble Chart XSS via File Names in JS String Literals

**Severity:** Medium (CVSS 6.1)  
**Root Cause:** File names embedded unescaped in single-quoted JavaScript string literals in Google Charts data table.

### Files to Modify
- `../graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java`

### TDD Tasks

#### 8.1 Add tests for JS string escaping
```java
// graph-data-generator/src/test/java/org/hjug/gdg/GraphDataGeneratorTest.java

@Test void generateBubbleChartData_escapesSingleQuotesInFileName() throws IOException
@Test void generateBubbleChartData_escapesBackslashesInFileName() throws IOException
@Test void generateBubbleChartData_escapesNewlinesInFileName() throws IOException
@Test void generateBubbleChartData_escapesCarriageReturnsInFileName() throws IOException
@Test void escapeJavaScriptString_handlesNull() throws IOException
@Test void escapeJavaScriptString_handlesEmpty() throws IOException
```

#### 8.2 Implement fix
- Add `escapeJavaScriptString(String value)` method:
  - Escape `\` → `\\`
  - Escape `'` → `\'`
  - Escape `\n` → `\n`
  - Escape `\r` → `\r`
- Apply to `rankedDisharmony.getFileName()` in `generateBubbleChartData()` (line ~44-46)

---

## Finding 9: BUG-R2-S2-A2-H4 — Package Map XSS via Kotlin Package Names in Template Literals

**Severity:** Medium (CVSS 6.9)  
**Root Cause:** Kotlin package names (from backtick identifiers) embedded in DOT inside JS template literal with only `.` → `_` replacement.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

### TDD Tasks

#### 9.1 Add tests for package map escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/HtmlReportTest.java

@Test void renderPackageVertices_escapesPackageNameForNodeId() throws IOException
@Test void renderPackageVertices_escapesPackageNameForLabel() throws IOException
@Test void renderPackageGraphEdge_escapesPackageNameForEdgeEndpoints() throws IOException
@Test void renderSafePackageNodeId_handlesDollarSign() throws IOException
@Test void renderSafePackageNodeId_handlesQuotes() throws IOException
@Test void renderSafePackageNodeId_handlesBraces() throws IOException
@Test void escapeDotQuoted_escapesBackslash() throws IOException
@Test void escapeDotQuoted_escapesDoubleQuote() throws IOException
@Test void escapeDotQuoted_escapesDollarForTemplateLiteral() throws IOException
```

#### 9.2 Implement fix
- Add `renderSafePackageNodeId(String packageName)`:
  - Replace `.` → `_`
  - Replace `$` → `_`
  - Replace `"` → `_`
  - Replace `{` → `_`
  - Replace `}` → `_`
- Add `escapeDotQuoted(String value)`:
  - Escape `\` → `\\`
  - Escape `"` → `\"`
  - Escape `$` → `\$` (prevent `${...}` interpolation)
- Apply in `renderPackageVertices()` and `renderPackageGraphEdge()`
- Also apply `escapeDotQuoted` to class labels in `renderClassVertices()`

---

## Finding 10: BUG-R2-S2-A3-H1 — CSV Formula Injection via POM Name/Version

**Severity:** Medium (CVSS 6.1)  
**Root Cause:** POM `<name>`/`<version>` written unescaped as cell #1 of every CSV data row.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`

### TDD Tasks

#### 10.1 Add tests for CSV formula neutralization
```java
// report/src/test/java/org/hjug/refactorfirst/report/CsvReportTest.java

@Test void execute_sanitizesProjectVersionInDataRows() throws IOException
@Test void execute_sanitizesProjectNameInNoGitFallback() throws IOException
@Test void execute_sanitizesProjectVersionInNoGitFallback() throws IOException
@Test void execute_sanitizesProjectNameInNoGodClassesFallback() throws IOException
@Test void execute_sanitizesProjectVersionInNoGodClassesFallback() throws IOException
@Test void sanitizeCsvCell_prefixesFormulaTriggersWithApostrophe() throws IOException
@Test void sanitizeCsvCell_handlesEqualsPrefix() throws IOException
@Test void sanitizeCsvCell_handlesPlusPrefix() throws IOException
@Test void sanitizeCsvCell_handlesMinusPrefix() throws IOException
@Test void sanitizeCsvCell_handlesAtPrefix() throws IOException
@Test void sanitizeCsvCell_handlesTabPrefix() throws IOException
@Test void sanitizeCsvCell_handlesCarriageReturnPrefix() throws IOException
@Test void sanitizeCsvCell_handlesEmbeddedNewlineWithFormula() throws IOException
@Test void sanitizeCsvCell_quotesAllValues() throws IOException
@Test void sanitizeCsvCell_escapesEmbeddedQuotes() throws IOException
```

#### 10.2 Implement fix
- Add `sanitizeCsvCell(String value)` method:
  - Escape embedded `"` → `""`
  - Prefix with `'` if value starts with `=`, `+`, `-`, `@`, `\t`, `\r` (or after `\n`/`\r`)
  - Wrap entire value in `"`
- Apply to:
  - `projectVersion` in data row loop (line ~121)
  - `projectName`/`projectVersion` in no-git fallback (line ~63-70)
  - `projectName`/`projectVersion` in no-god-classes fallback (line ~99-100)
  - All cells in `addsRow()` (line ~208)

---

## Finding 11: BUG-R2-S2-A3-H2 — CSV Formula Injection via File Names/Paths

**Severity:** Medium (CVSS 6.1)  
**Root Cause:** File names and paths written unquoted/unneutralized into CSV cells.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`

### TDD Tasks

#### 11.1 Add tests for file name/path CSV escaping
```java
// report/src/test/java/org/hjug/refactorfirst/report/CsvReportTest.java

@Test void getDataList_escapesFileNameInClassCell() throws IOException
@Test void getDataList_escapesFullPathInDetailedMode() throws IOException
@Test void addsRow_escapesAllCellsWithSanitizeCsvCell() throws IOException
@Test void escapeCsvCell_quotesAndEscapesFormulaTriggers() throws IOException
@Test void escapeCsvCell_escapesEmbeddedQuotes() throws IOException
@Test void escapeCsvCell_handlesCommaInValue() throws IOException
```

#### 11.2 Implement fix
- Add `escapeCsvCell(String value)` method (RFC 4180 compliant + formula neutralization):
  - Escape `"` → `""`
  - Prefix `'` if starts with formula trigger
  - Wrap in `"`
- Apply in `addsRow()` for all `rankedDisharmonyData` cells
- Apply in `getDataList()` for fileName and path cells

---

## Finding 12: BUG-R2-S2-A5-H1 — Maven Plugin Output Directory Path Traversal

**Severity:** Medium (CVSS 4.4)  
**Root Cause:** Maven mojos use attacker-controlled `<reporting><outputDirectory>` without containment check.

### Files to Modify
- `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java`
- `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java`
- `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenJsonReport.java`
- `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java`
- `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`

### TDD Tasks

#### 12.1 Add tests for output directory containment
```java
// report/src/test/java/org/hjug/refactorfirst/report/ReportWriterTest.java

@Test void containReportDirectory_allowsNormalTargetSite() throws IOException
@Test void containReportDirectory_rejectsAbsolutePath() throws IOException
@Test void containReportDirectory_rejectsTraversalPath() throws IOException
@Test void containReportDirectory_rejectsEmptyValue_usesDefault() throws IOException
@Test void containReportDirectory_usesBaseDirAsRoot() throws IOException
```

```java
// refactor-first-maven-plugin/src/test/java/org/hjug/mavenreport/RefactorFirstHtmlReportTest.java

@Test void execute_usesContainedOutputDirectory() throws IOException
```

#### 12.2 Implement fix
- Add `ReportWriter.containReportDirectory(File baseDir, String configuredDir)`:
  - Resolve configured dir against baseDir
  - Normalize and verify it starts with baseDir
  - Default to `target/site` if empty/null
  - Throw `IllegalArgumentException` if escapes baseDir
- Update all 4 mojos to call `containReportDirectory(project.getBasedir(), ...)`

---

## Finding 13: BUG-R2-S2-A5-H2 — CSV Filename Path Traversal via POM Name/Version

**Severity:** Medium (CVSS 4.3)  
**Root Cause:** CSV filename composed from unsanitized POM `<name>`/`<version>` with path traversal.

### Files to Modify
- `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`

### TDD Tasks

#### 13.1 Add tests for filename sanitization
```java
// report/src/test/java/org/hjug/refactorfirst/report/CsvReportTest.java

@Test void execute_sanitizesProjectNameInFilename() throws IOException
@Test void execute_sanitizesProjectVersionInFilename() throws IOException
@Test void sanitizeFilenameSegment_replacesPathSeparators() throws IOException
@Test void sanitizeFilenameSegment_replacesTraversalSegments() throws IOException
@Test void sanitizeFilenameSegment_replacesControlCharacters() throws IOException
@Test void sanitizeFilenameSegment_handlesNullOrBlank() throws IOException
```

#### 13.2 Implement fix
- Add `sanitizeFilenameSegment(String value)`:
  - Replace non-word, non-dot, non-hyphen with `_`
  - Replace `..` sequences with `_`
  - Return `"unknown"` for null/blank
- Apply to `projectName` and `projectVersion` in filename composition (line ~24-33)

---

## Execution Order (Dependencies)

### Phase 1: Core Infrastructure (No Dependencies)
1. **Finding 7** (ReportWriter symlink) - Foundation for all file writes
2. **Finding 12** (Maven output directory containment) - Uses ReportWriter helper

### Phase 2: Input Validation (Shared by Multiple Findings)
3. **Finding 1** (GitLogReader URL validation) - Used by Findings 1, 5
4. **Finding 2** (POM name/version escaping) - Used by Findings 2, 10, 13

### Phase 3: HTML Report XSS Fixes (Depend on Phase 1-2)
5. **Finding 3** (File name/path escaping in tables)
6. **Finding 4** (Kotlin identifiers in cycle/method/package tables)
7. **Finding 5** (DOT-in-script URL escaping) - Reuses Finding 1 fix
8. **Finding 6** (Cycle map visuals Kotlin name sanitization)
9. **Finding 8** (Bubble chart JS string escaping)
10. **Finding 9** (Package map template literal escaping)

### Phase 4: CSV Fixes (Depend on Phase 2)
11. **Finding 10** (CSV formula injection via POM values)
12. **Finding 11** (CSV formula injection via file names/paths)
13. **Finding 13** (CSV filename path traversal)

---

## Test Infrastructure Requirements

### Test Dependencies (verify in pom.xml)
- JUnit 5 (Jupiter)
- AssertJ or Hamcrest for assertions
- Temporary directory support (`@TempDir`)
- Mockito for mocking GitLogReader, MavenProject, etc.

### Test Fixtures Needed
- Malicious `.git/config` with XSS payloads in `remote.origin.url`
- Malicious `../pom.xml` with XSS/formula payloads in `<name>`/`<version>`
- Test Kotlin files with backtick identifiers containing payloads
- Test Java files with malicious file names
- Symlink test fixtures (requires Linux/macOS or Git Bash on Windows)

### Test Commands
```bash
# Run all tests
mvn clean test

# Run specific module tests
mvn clean test -pl change-proneness-ranker
mvn clean test -pl report
mvn clean test -pl graph-data-generator
mvn clean test -pl cost-benefit-calculator
mvn clean test -pl refactor-first-maven-plugin

# Run single test class
mvn clean test -pl report -Dtest=SimpleHtmlReportTest
mvn clean test -pl change-proneness-ranker -Dtest=GitLogReaderTest
```

---

## Verification Checklist Per Finding

For each finding, verify:
- [ ] Failing unit tests written first (red)
- [ ] Production code implemented (green)
- [ ] All tests pass (refactor if needed)
- [ ] Integration test with malicious fixture passes
- [ ] `mvn spotless:check` passes
- [ ] `mvn clean install -DskipTests` succeeds

---

## Integration Test Fixtures

Create test fixtures in `test-resources/src/test/resources/` for each finding:
- `finding-1-git-config-xss/` - `.git/config` with malicious remote URL
- `finding-2-pom-xss/` - `../pom.xml` with `<name><script>...</script></name>`
- `finding-3-filename-xss/` - Java file named `Pwn<img src=x onerror=alert(1)>.java`
- `finding-4-kotlin-xss/` - Kotlin file with backtick class names
- `finding-5-dot-script-xss/` - Combined with finding-1
- `finding-6-cycle-map-xss/` - Kotlin cycle with payload class names
- `finding-7-symlink/` - Repo with symlink in target/site
- `finding-8-bubble-chart-xss/` - Java file with `'` in name
- `finding-9-package-map-xss/` - Kotlin package with `${...}` payload
- `finding-10-csv-formula-pom/` - POM with `=WEBSERVICE(...)` version
- `finding-11-csv-formula-filename/` - Java file with formula name
- `finding-12-maven-output-traversal/` - POM with `<outputDirectory>../../../tmp</outputDirectory>`
- `finding-13-csv-filename-traversal/` - POM with `<name>evil/../../../tmp/planted</name>`

---

## Final Validation

After all 13 findings implemented:
1. Run full build: `mvn clean install`
2. Run OWASP dependency check: `mvn clean install -Plocal`
3. Verify all tests pass: `mvn clean test`
4. Verify formatting: `mvn spotless:check`
5. Manual verification with malicious fixtures against CLI and Maven plugin