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
- Fat jar location: `cli/target/refactor-first-cli-*.jar`

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
`mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.8.0:htmlReport`

Configuration options (most important):
- `showDetails`: Shows God Class metrics in table (default: false)
- `backEdgeAnalysisCount`: 0 = analyze all back edges (default: 50)
- `analyzeCycles`: Whether to analyze cycles (default: true)
- `excludeTests`: Exclude test classes (default: true)
- `minifyHtml`: Minify HTML report (default: false)