# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RefactorFirst is a Java static analysis tool that scans Git repositories, detects code disharmonies (God Classes, Brain Classes, highly coupled classes, circular dependencies), and produces prioritized refactoring recommendations in HTML/CSV/JSON reports. The core insight is combining code-quality metrics with Git change history so the most painful classes surface first.

## Build Commands

```bash
# Full build (compile + test + package)
mvn clean install

# Skip tests for faster iteration
mvn clean install -DskipTests

# Run all tests
mvn clean test

# Run tests in a single module
mvn clean test -pl effort-ranker

# Run a single test class
mvn clean test -pl effort-ranker -Dtest=BrainClassTest

# Format code (Palantir Java format via Spotless)
mvn spotless:apply

# Check formatting without modifying
mvn spotless:check

# Build with OWASP dependency check (slow)
mvn clean install -Plocal
```

The CLI fat jar is produced at `cli/target/refactor-first-cli-*.jar` via the Maven Shade plugin.

## Module Architecture

11-module Maven build. Data flows left-to-right:

```
codebase-graph-builder  ──→  cost-benefit-calculator  ──→  graph-data-generator
graph-algorithms         ──→        │                  ──→  report
change-proneness-ranker  ──→        │                  ──→  cli
effort-ranker            ──→  ──────┘                  ──→  refactor-first-maven-plugin
test-resources (shared fixtures)
coverage (JaCoCo aggregation)
```

**codebase-graph-builder** — The most complex module. Uses OpenRewrite to parse Java source across versions (11/17/21), builds class and package dependency graphs with JGraphT, detects cycle-breaking candidates using two graph algorithms, and returns everything in `CodebaseGraphDTO`. Entry point: `JavaGraphBuilder.getCodebaseGraphDTO()`.

**graph-algorithms** — Two cycle-decomposition algorithms used by `JavaGraphBuilder`:
- *Directed Feedback Vertex Set* (`org.hjug.feedback.vertex.kernelized`) — kernelized algorithm; identifies the minimum vertex set to remove to break all cycles.
- *Feedback Arc Set with PageRank* (`org.hjug.feedback.arc.pageRank`) — identifies edges to remove; uses PageRank on the line digraph. Based on Geladaris et al.
- See `DIAGRAM.md` files in each algorithm package for pseudocode diagrams.

**effort-ranker** — Runs PMD rules (`category/java/design.xml` + custom `CBORule`) to detect God Classes (ATFD, WMC, TCC) and highly coupled classes (CBO). Also detects Brain Classes, Data Classes, Feature Envy, and several other disharmonies in `DisharmonyDetector`.

**change-proneness-ranker** — Uses JGit to read Git commit history and assign change-frequency scores to classes.

**cost-benefit-calculator** — Orchestrates the full pipeline: runs effort-ranker and change-proneness-ranker, combines scores into `RankedDisharmony` objects. Main orchestration class: `CostBenefitCalculator`.

**report** — Generates HTML (with embedded bubble charts), CSV, and JSON output. Reports with >4000 classes switch to a simplified 3D viewer.

**cli** — PicoCLI-based executable. Entry: `org.hjug.refactorfirst.Main` → `ReportCommand`.

**refactor-first-maven-plugin** — Maven plugin wrapper; key config options: `showDetails`, `backEdgeAnalysisCount` (default 50; set 0 for all), `analyzeCycles`, `excludeTests`.

## Key Data Model

`CodebaseGraphDTO` is the central transfer object passed between modules. It holds:
- JGraphT directed graphs for class and package dependencies
- Detected disharmony lists (God Classes, Brain Classes, etc.)
- Metrics per class (ATFD, WMC, TCC, CBO)

`RankedDisharmony` carries the final prioritized output consumed by report generators.

## Architectural Patterns

- **Visitor** — `JavaVisitor` (OpenRewrite) walks the AST to collect class dependencies.
- **Pipeline** — `CostBenefitCalculator` chains multiple independent rankers then merges results.
- **DTO** — `CodebaseGraphDTO` decouples parsing from ranking and reporting.
- Lombok `@Data`/`@Builder` is used extensively; avoid adding boilerplate that Lombok already removes.

## Testing

JUnit 5 (Jupiter) with parameterized tests. Test fixtures live in `test-resources/src/test/resources`. When adding or modifying graph-algorithm behavior, check `JavaGraphBuilderTest` and `CircularReferenceCheckerTests` for integration-level coverage.

Mutation testing via PIT (`pitest-maven`) is configured but not part of the default build; run explicitly if needed.

## Java & Toolchain

- Source/target: Java 11 minimum; OpenRewrite parser supports 11, 17, 21.
- Logging: SLF4J; use `log.debug()` for verbose per-class output, `log.info()` sparingly.
- Spotless enforces Palantir Java format — run `mvn spotless:apply` before committing.
