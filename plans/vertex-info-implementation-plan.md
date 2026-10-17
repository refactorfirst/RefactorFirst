# VertexInfo Implementation Plan

## Goal

Replace bare `String` FQNs/package names as graph vertices with a rich `VertexInfo`
object that carries the information needed to build a composed codebase map
rendered as part of the graph image:

- `name` — the vertex name (class FQN or package name)
- `locationOnDisk` — location of the vertex relative to the repository root
  (source file path for classes; member-file directory for packages)
- `parentNamespace` — the parent package if the vertex is a package, or the
  enclosing construct (package or class) for non-package vertices that contain code

When graph dependency information is collected, the collector now creates
`VertexInfo` instances as graph vertices instead of raw names, and all code that
references vertices fetches the name via `VertexInfo` (`getName()` /
`VertexInfo.forName(...)` probes).

## Design decisions

1. **`VertexInfo` lives in `codebase-graph-builder`** (`org.hjug.graphbuilder`).
   It is the module that owns graph construction; every consumer module
   (cost-benefit-calculator, report) already depends on it.
   `graph-algorithms` must NOT depend on it — its String-bound helpers are
   genericized instead (they contain no name-specific logic).

2. **Name-based identity**: `equals`/`hashCode` consider only `name`. This keeps
   JGraphT vertex dedup semantics identical to the previous `String` vertices and
   lets code probe the graph with cheap `VertexInfo.forName(name)` lookups.

3. **`toString()` returns `name`**: `DefaultWeightedEdge.toString()`
   (`"(source : target)"`) and the `extractVertexes(edge)` parsing in the reports
   continue to work unchanged.

4. **`parentNamespace` is derived at creation time** (pure function of the name):
   - `pkg.Outer$Inner` → `pkg.Outer` (enclosing class, `$` separator)
   - `pkg.Outer$1` / `pkg.Outer$` → `pkg.Outer` (anonymous/synthetic)
   - `pkg.Outer.<anonymous>` → `pkg.Outer`; bare `<anonymous>` → `null`
   - `com.foo.Bar` → `com.foo` (enclosing package)
   - package `com.foo` → `com` (parent package); top-level segment → `null`

5. **`locationOnDisk` is hydrated after the visitor walk** from the canonical
   repo-relative `classToSourceFilePathMapping` (with `$`-prefix and
   `<anonymous>`-enclosing fallbacks), because canonicalization needs
   `repositoryRoot`, which is only known to the builders/visitors. A new
   `VertexInfoHydrator` populates class vertices (source file path) and package
   vertices (directory of a member file). Package directories are derived by
   dropping the file name from a member's mapped path.

6. **Report/JSON DTOs stay name-based**: `RankedCycle`, `CycleNode`,
   `RankedDisharmony`, JSON models etc. keep `String` class names; the
   boundary code extracts them with `VertexInfo::getName`. Removal sets
   (`classesToRemove`, `packagesToRemove`) pass through as `Set<VertexInfo>`
   and are probed with `VertexInfo.forName(name)` where names are rendered.

## Phases (each TDD: red-green-refactor)

### Phase 1 — `VertexInfo` (codebase-graph-builder)
New test `VertexInfoTest` (Given-When-Then): construction, `forName` factory,
parent-namespace derivation rules, name-based equality, `toString() == name`.
Then implement `VertexInfo` (hand-written equals/hashCode/toString; mutable
`locationOnDisk` for hydration).

### Phase 2 — `VertexInfoHydrator` (codebase-graph-builder)
New test `VertexInfoHydratorTest`: given class/package graphs + canonical
mapping, hydration sets `locationOnDisk` for
- mapped classes,
- `$`-nested/anonymous classes via the enclosing class entry,
- Kotlin `<anonymous>` via the enclosing entry,
- packages via a member file's directory,
and never overwrites a name-derived `parentNamespace` (already set).
Then implement `VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping)`.

### Phase 3 — Collectors emit `VertexInfo` vertices
Update `GraphDependencyCollectorTest` first (expects `VertexInfo` vertices with
`parentNamespace` populated at collection time), then change
`GraphDependencyCollector` and `GraphMetricsCollector` to
`Graph<VertexInfo, DefaultWeightedEdge>` with a name-keyed vertex registry.
`recordClassLocation` now stores locations captured during collection.

### Phase 4 — DTO + builders + composite merge
`CodebaseGraphDTO` graphs become `Graph<VertexInfo, DefaultWeightedEdge>`.
`JavaSourceFileGraphBuilder.finalizeDto` (shared with Kotlin builder) hydrates
vertices before assembling the DTO. `CompositeGraphBuilder.merge` operates on
`VertexInfo` (name-based probes), re-hydrates after anonymous-mapping and
reconciliation. Fix module tests (`JavaGraphBuilderTest`, `KotlinGraphBuilderTest`,
`CompositeGraphBuilder*Test`, `Java25AnalysisIntegrationTest`,
`TypeParameterReferenceTest`, `GraphMetricsCollectorTest`, visitor tests).

### Phase 5 — Genericize graph-algorithms
`EdgeRemovalCalculator<V>`, `EdgeInfoCalculator<V>`,
`SparseIntDWGEdgeRemovalCalculator<V>`, `CycleRemovalResult<V>`,
`CycleRemovalComputer.<V>` — no behavior change; existing tests keep passing
via inference (`V = String`), which is the regression proof.

### Phase 6 — cost-benefit-calculator
`CycleRanker.rankCycles(Graph<VertexInfo, ...>)` builds name-based
`RankedCycle`/`CycleNode` via `getName()`.
`CostBenefitCalculator.calculateRelationshipCostBenefitValues` takes
`Graph<VertexInfo, ...>`, `Set<VertexInfo>` and
`Map<VertexInfo, AsSubgraph<VertexInfo, ...>>` and extracts names at the
`RankedDisharmony` boundary. Fix cbc tests.

### Phase 7 — report module
`HtmlReport`, `SimpleHtmlReport`, `JsonGenerator`: field/signature types change to
`VertexInfo` graphs; DOT/HTML rendering stays name-based via
`extractVertexes(edge)` (relies on `toString()`) with `VertexInfo.forName`
probes for `contains` checks. Fix report tests.

### Phase 8 — Verify
`mvn spotless:apply`, then full `mvn clean install` (all modules, tests on).

## Blast radius (main code)
- codebase-graph-builder: GraphDependencyCollector, GraphMetricsCollector,
  CodebaseGraphDTO, CompositeGraphBuilder, JavaSourceFileGraphBuilder,
  KotlinSourceFileGraphBuilder
- graph-algorithms: EdgeRemovalCalculator, EdgeInfoCalculator,
  SparseIntDWGEdgeRemovalCalculator, CycleRemovalResult, CycleRemovalComputer
- cost-benefit-calculator: CycleRanker, CostBenefitCalculator
- report: HtmlReport, SimpleHtmlReport, JsonGenerator
- untouched: cli, maven plugin, gradle plugin, effort-ranker,
  change-proneness-ranker, graph-data-generator (no graph vertex contact)
