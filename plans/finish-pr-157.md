# Implementation Plan: Finish PR #157 - Gradle Plugin Support

## Objective
Complete the Gradle plugin implementation for RefactorFirst to provide the same capabilities as the existing Maven plugin, with Java 21 as the current version ceiling, using Test-Driven Development (TDD) methodology.

## Java Version Support Policy
- **Supported Versions**: Java 11, 17, 21
- **Version Ceiling**: Java 21 (maximum supported in this iteration)
- **Future Support**: Java 25 support will be added in a future iteration
- **Error Handling**: Provide clear error messages for Java versions beyond 21

## Implementation Tasks

### Phase 1: Core Plugin Implementation (TDD Approach)

#### Task 1.1: Test and Implement RefactorFirstPluginException
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/RefactorFirstPluginExceptionTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/RefactorFirstPluginException.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefactorFirstPluginExceptionTest {

    @Test
    void exceptionFormatsMessageWithPrefix() {
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error");
        
        assertTrue(exception.getMessage().startsWith("RefactorFirst plugin error:"));
        assertTrue(exception.getMessage().contains("test error"));
    }

    @Test
    void exceptionIncludesHelpLink() {
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error");
        
        assertTrue(exception.getMessage().contains("https://github.com/refactorfirst/RefactorFirst/wiki/Troubleshooting"));
    }

    @Test
    void exceptionWithCausePreservesCause() {
        Throwable cause = new RuntimeException("original cause");
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error", cause);
        
        assertEquals(cause, exception.getCause());
    }

    @Test
    void exceptionIsGradleException() {
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error");
        
        assertTrue(exception instanceof org.gradle.api.GradleException);
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import org.gradle.api.GradleException;

public class RefactorFirstPluginException extends GradleException {
    public RefactorFirstPluginException(String message, Throwable cause) {
        super(formatErrorMessage(message), cause);
    }

    public RefactorFirstPluginException(String message) {
        super(formatErrorMessage(message));
    }

    private static String formatErrorMessage(String message) {
        return "RefactorFirst plugin error: " + message + "\n" +
               "For help, see: https://github.com/refactorfirst/RefactorFirst/wiki/Troubleshooting";
    }
}
```

**Step 3: Refactor if needed**

#### Task 1.2: Test and Implement RefactorFirstExtension
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/RefactorFirstExtensionTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/RefactorFirstExtension.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class RefactorFirstExtensionTest {
    private RefactorFirstExtension extension;

    @BeforeEach
    void setUp() {
        Project project = ProjectBuilder.builder().build();
        extension = project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);
    }

    @Test
    void hasDefaultValues() {
        assertEquals(false, extension.getShowDetails().get());
        assertEquals(50, extension.getBackEdgeAnalysisCount().get());
        assertEquals(true, extension.getAnalyzeCycles().get());
        assertEquals(true, extension.getExcludeTests().get());
        assertEquals(false, extension.getMinifyHtml().get());
    }

    @Test
    void validatePassesWithValidConfiguration() {
        extension.getBackEdgeAnalysisCount().set(100);
        
        assertDoesNotThrow(() -> extension.validate());
    }

    @Test
    void validateThrowsExceptionForNegativeBackEdgeAnalysisCount() {
        extension.getBackEdgeAnalysisCount().set(-1);
        
        RefactorFirstPluginException exception = assertThrows(
            RefactorFirstPluginException.class,
            () -> extension.validate()
        );
        
        assertTrue(exception.getMessage().contains("backEdgeAnalysisCount must be >= 0"));
    }

    @Test
    void validateThrowsExceptionForZeroBackEdgeAnalysisCount() {
        extension.getBackEdgeAnalysisCount().set(0);
        
        assertDoesNotThrow(() -> extension.validate(), "Zero should be valid");
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import org.gradle.api.provider.Property;

public abstract class RefactorFirstExtension {
    public abstract Property<Boolean> getShowDetails();
    public abstract Property<Integer> getBackEdgeAnalysisCount();
    public abstract Property<Boolean> getAnalyzeCycles();
    public abstract Property<Boolean> getExcludeTests();
    public abstract Property<Boolean> getMinifyHtml();
    public abstract Property<java.io.File> getTestSourceDirectory();
    public abstract Property<java.io.File> getOutputDirectory();

    public RefactorFirstExtension() {
        getShowDetails().convention(false);
        getBackEdgeAnalysisCount().convention(50);
        getAnalyzeCycles().convention(true);
        getExcludeTests().convention(true);
        getMinifyHtml().convention(false);
    }

    public void validate() {
        if (getBackEdgeAnalysisCount().get() < 0) {
            throw new RefactorFirstPluginException(
                "backEdgeAnalysisCount must be >= 0, got: " + getBackEdgeAnalysisCount().get());
        }
    }
}
```

**Step 3: Refactor if needed**

#### Task 1.3: Test and Implement GradleProjectAdapter
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/GradleProjectAdapterTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/GradleProjectAdapter.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradleProjectAdapterTest {
    private Project project;
    private RefactorFirstExtension extension;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        extension = project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);
    }

    @Test
    void getSourceDirectoriesReturnsMainSourceSet() {
        extension.getExcludeTests().set(true);
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);
        
        List<File> sources = adapter.getSourceDirectories();
        
        assertFalse(sources.isEmpty(), "Should return main source directories");
    }

    @Test
    void getSourceDirectoriesIncludesTestSourcesWhenNotExcluded() {
        extension.getExcludeTests().set(false);
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);
        
        List<File> sources = adapter.getSourceDirectories();
        
        assertTrue(sources.size() >= 2, "Should include both main and test sources");
    }

    @Test
    void getProjectBaseDirReturnsProjectDirectory() {
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);
        
        File baseDir = adapter.getProjectBaseDir();
        
        assertEquals(project.getProjectDir(), baseDir);
    }

    @Test
    void getProjectNameReturnsProjectName() {
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);
        
        String name = adapter.getProjectName();
        
        assertEquals(project.getName(), name);
    }

    @Test
    void getProjectVersionReturnsProjectVersion() {
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);
        
        String version = adapter.getProjectVersion();
        
        assertEquals(project.getVersion().toString(), version);
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;

public class GradleProjectAdapter {
    private final Project project;
    private final RefactorFirstExtension extension;

    public GradleProjectAdapter(Project project, RefactorFirstExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    public List<File> getSourceDirectories() {
        List<File> sources = new ArrayList<>();
        SourceSetContainer sourceSets = project.getExtensions()
            .getByType(SourceSetContainer.class);

        // Main source sets
        sources.addAll(sourceSets.getByName("main")
            .getAllJava()
            .getSrcDirs());

        // Test source sets if not excluded
        if (!extension.getExcludeTests().get()) {
            sources.addAll(sourceSets.getByName("test")
                .getAllJava()
                .getSrcDirs());
        }
        return sources;
    }

    public File getProjectBaseDir() {
        return project.getProjectDir();
    }

    public String getProjectName() {
        return project.getName();
    }

    public String getProjectVersion() {
        return project.getVersion().toString();
    }
}
```

**Step 3: Refactor if needed**

#### Task 1.4: Test and Implement HtmlReportTask
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/HtmlReportTaskTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/HtmlReportTask.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class HtmlReportTaskTest {
    private Project project;
    private HtmlReportTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks().register("refactorFirstHtmlReport", HtmlReportTask.class).get();
    }

    @Test
    void taskIsCacheable() {
        assertTrue(task.getClass().isAnnotationPresent(org.gradle.api.tasks.CacheableTask.class));
    }

    @Test
    void taskHasOutputFileProperty() {
        assertNotNull(task.getReportFile());
    }

    @Test
    void taskHasExtensionProperty() {
        assertNotNull(task.getExtension());
    }

    @Test
    void taskHasProjectAdapterProperty() {
        assertNotNull(task.getProjectAdapter());
    }

    @Test
    void taskHasProjectNameProperty() {
        assertNotNull(task.getProjectName());
    }

    @Test
    void taskHasProjectVersionProperty() {
        assertNotNull(task.getProjectVersion());
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.SimpleHtmlReport;

@CacheableTask
public abstract class HtmlReportTask extends DefaultTask {

    public abstract Property<RefactorFirstExtension> getExtension();
    public abstract Property<GradleProjectAdapter> getProjectAdapter();
    public abstract Property<String> getProjectName();
    public abstract Property<String> getProjectVersion();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void generate() {
        RefactorFirstExtension extension = getExtension().get();
        extension.validate();

        GradleProjectAdapter adapter = getProjectAdapter().get();
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();

        htmlReport.execute(
            extension.getBackEdgeAnalysisCount().get(),
            extension.getAnalyzeCycles().get(),
            extension.getShowDetails().get(),
            extension.getMinifyHtml().get(),
            extension.getExcludeTests().get(),
            extension.getTestSourceDirectory().isPresent() ?
                extension.getTestSourceDirectory().get().getAbsolutePath() : null,
            getProjectName().get(),
            getProjectVersion().get(),
            adapter.getProjectBaseDir(),
            extension.getOutputDirectory().get().getAbsolutePath()
        );
    }
}
```

**Step 3: Refactor if needed**

#### Task 1.5: Test and Implement RefactorFirstPlugin
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/RefactorFirstPluginTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/RefactorFirstPlugin.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class RefactorFirstPluginTest {
    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
    }

    @Test
    void pluginCreatesExtension() {
        new RefactorFirstPlugin().apply(project);
        
        assertNotNull(project.getExtensions().findByName("refactorFirst"));
    }

    @Test
    void pluginRegistersHtmlReportTask() {
        new RefactorFirstPlugin().apply(project);
        
        TaskProvider<?> task = project.getTasks().named("refactorFirstHtmlReport");
        assertNotNull(task);
    }

    @Test
    void extensionHasDefaultValues() {
        new RefactorFirstPlugin().apply(project);
        
        RefactorFirstExtension extension = project.getExtensions()
            .getByType(RefactorFirstExtension.class);
        
        assertEquals(false, extension.getShowDetails().get());
        assertEquals(50, extension.getBackEdgeAnalysisCount().get());
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class RefactorFirstPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Create extension
        RefactorFirstExtension extension = project.getExtensions()
            .create("refactorFirst", RefactorFirstExtension.class);

        // Create project adapter
        GradleProjectAdapter projectAdapter = new GradleProjectAdapter(project, extension);

        // Register HTML report task
        project.getTasks().register("refactorFirstHtmlReport", HtmlReportTask.class, task -> {
            task.getExtension().set(extension);
            task.getProjectAdapter().set(projectAdapter);
            task.getProjectName().set(project.getName());
            task.getProjectVersion().set(project.getVersion().toString());
            task.getReportFile().set(
                project.getLayout().getBuildDirectory()
                    .file("reports/refactor-first/refactor-first-report.html")
            );
        });

        // TODO: Register CSV and JSON report tasks in subsequent implementation
    }
}
```

**Step 3: Refactor if needed**

### Phase 2: Build Configuration

#### Task 2.1: Update build.gradle
**File**: `../refactor-first-gradle-plugin/build.gradle`

Update the Gradle build configuration:

```gradle
plugins {
    id 'java-gradle-plugin'
    id 'maven-publish'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven { url 'target/dependencies' }
    mavenLocal()
}

dependencies {
    compileOnly gradleApi()
    implementation "org.hjug.refactorfirst.graphdatagenerator:graph-data-generator:${version}"
    implementation "org.hjug.refactorfirst.report:report:${version}"
}

gradlePlugin {
    plugins {
        refactorFirstPlugin {
            id = 'org.hjug.refactorfirst'
            displayName = 'RefactorFirst'
            description = 'Plugin that identifies God classes and other code disharmonies in a codebase and suggests which classes should be refactored first.'
            implementationClass = 'org.hjug.gradlereport.RefactorFirstPlugin'
        }
    }
}

publishing {
    publications {
        maven(MavenPublication) {
            from components.java

            pom {
                name = 'RefactorFirst Gradle Plugin'
                description = 'Plugin that identifies God classes and other code disharmonies in a codebase and suggests which classes should be refactored first.'
                url = 'https://github.com/refactorfirst/RefactorFirst'

                licenses {
                    license {
                        name = 'Apache License 2.0'
                        url = 'http://www.apache.org/licenses/'
                    }
                }

                developers {
                    developer {
                        name = 'Jim Bethancourt'
                        email = 'jimbethancourt@gmail.com'
                    }
                }

                scm {
                    connection = 'scm:git:https://github.com/refactorfirst/RefactorFirst'
                    developerConnection = 'scm:git:https://github.com/refactorfirst/RefactorFirst'
                    url = 'https://github.com/refactorfirst/RefactorFirst'
                }
            }
        }
    }
}
```

#### Task 2.2: Update parent pom.xml
**File**: `../pom.xml`

Uncomment the Gradle plugin module in the parent POM:

```xml
<modules>
    <module>test-resources</module>
    <module>codebase-graph-builder</module>
    <module>graph-algorithms</module>
    <module>change-proneness-ranker</module>
    <module>effort-ranker</module>
    <module>cost-benefit-calculator</module>
    <module>graph-data-generator</module>
    <module>refactor-first-maven-plugin</module>
    <module>refactor-first-gradle-plugin</module>
    <module>coverage</module>
    <module>report</module>
    <!--<module>cli</module>-->
</modules>
```

#### Task 2.3: Update Gradle plugin pom.xml
**File**: `../refactor-first-gradle-plugin/pom.xml`

Update dependencies to include both required modules:

```xml
<dependencies>
    <dependency>
        <groupId>org.hjug.refactorfirst.graphdatagenerator</groupId>
        <artifactId>graph-data-generator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.hjug.refactorfirst.report</groupId>
        <artifactId>report</artifactId>
    </dependency>
</dependencies>
```

### Phase 3: Additional Task Implementation (TDD Approach)

#### Task 3.1: Test and Implement CsvReportTask
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/CsvReportTaskTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/CsvReportTask.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class CsvReportTaskTest {
    private Project project;
    private CsvReportTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks().register("refactorFirstCsvReport", CsvReportTask.class).get();
    }

    @Test
    void taskIsCacheable() {
        assertTrue(task.getClass().isAnnotationPresent(org.gradle.api.tasks.CacheableTask.class));
    }

    @Test
    void taskHasOutputFileProperty() {
        assertNotNull(task.getReportFile());
    }

    @Test
    void taskHasExtensionProperty() {
        assertNotNull(task.getExtension());
    }

    @Test
    void taskHasProjectAdapterProperty() {
        assertNotNull(task.getProjectAdapter());
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.CsvReport;

@CacheableTask
public abstract class CsvReportTask extends DefaultTask {

    public abstract Property<RefactorFirstExtension> getExtension();
    public abstract Property<GradleProjectAdapter> getProjectAdapter();
    public abstract Property<String> getProjectName();
    public abstract Property<String> getProjectVersion();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void generate() {
        RefactorFirstExtension extension = getExtension().get();
        extension.validate();

        GradleProjectAdapter adapter = getProjectAdapter().get();
        CsvReport csvReport = new CsvReport();

        csvReport.execute(
            extension.getBackEdgeAnalysisCount().get(),
            extension.getAnalyzeCycles().get(),
            extension.getExcludeTests().get(),
            extension.getTestSourceDirectory().isPresent() ?
                extension.getTestSourceDirectory().get().getAbsolutePath() : null,
            getProjectName().get(),
            getProjectVersion().get(),
            adapter.getProjectBaseDir(),
            extension.getOutputDirectory().get().getAbsolutePath()
        );
    }
}
```

**Step 3: Refactor if needed**

#### Task 3.2: Test and Implement JsonReportTask
**Test File**: `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/JsonReportTaskTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/JsonReportTask.java`

**Step 1: Write Failing Test (Red)**
```java
package org.hjug.gradlereport;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class JsonReportTaskTest {
    private Project project;
    private JsonReportTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks().register("refactorFirstJsonReport", JsonReportTask.class).get();
    }

    @Test
    void taskIsCacheable() {
        assertTrue(task.getClass().isAnnotationPresent(org.gradle.api.tasks.CacheableTask.class));
    }

    @Test
    void taskHasOutputFileProperty() {
        assertNotNull(task.getReportFile());
    }

    @Test
    void taskHasExtensionProperty() {
        assertNotNull(task.getExtension());
    }

    @Test
    void taskHasProjectAdapterProperty() {
        assertNotNull(task.getProjectAdapter());
    }
}
```

**Step 2: Implement to Pass Test (Green)**
```java
package org.hjug.gradlereport;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.JsonReport;

@CacheableTask
public abstract class JsonReportTask extends DefaultTask {

    public abstract Property<RefactorFirstExtension> getExtension();
    public abstract Property<GradleProjectAdapter> getProjectAdapter();
    public abstract Property<String> getProjectName();
    public abstract Property<String> getProjectVersion();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void generate() {
        RefactorFirstExtension extension = getExtension().get();
        extension.validate();

        GradleProjectAdapter adapter = getProjectAdapter().get();
        JsonReport jsonReport = new JsonReport();

        jsonReport.execute(
            extension.getBackEdgeAnalysisCount().get(),
            extension.getAnalyzeCycles().get(),
            extension.getExcludeTests().get(),
            extension.getTestSourceDirectory().isPresent() ?
                extension.getTestSourceDirectory().get().getAbsolutePath() : null,
            getProjectName().get(),
            getProjectVersion().get(),
            adapter.getProjectBaseDir(),
            extension.getOutputDirectory().get().getAbsolutePath()
        );
    }
}
```

**Step 3: Refactor if needed**

#### Task 3.3: Test and Update RefactorFirstPlugin with Additional Tasks
**Test File**: Update `../refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/RefactorFirstPluginTest.java`
**Implementation File**: `../refactor-first-gradle-plugin/src/main/java/org/hjug/gradlereport/RefactorFirstPlugin.java`

**Step 1: Add Failing Test (Red)**
```java
// Add to RefactorFirstPluginTest class
@Test
void pluginRegistersCsvReportTask() {
    new RefactorFirstPlugin().apply(project);
    
    TaskProvider<?> task = project.getTasks().named("refactorFirstCsvReport");
    assertNotNull(task);
}

@Test
void pluginRegistersJsonReportTask() {
    new RefactorFirstPlugin().apply(project);
    
    TaskProvider<?> task = project.getTasks().named("refactorFirstJsonReport");
    assertNotNull(task);
}
```

**Step 2: Implement to Pass Test (Green)**
```java
// Add to the apply() method after HtmlReportTask registration

// Register CSV report task
project.getTasks().register("refactorFirstCsvReport", CsvReportTask.class, task -> {
    task.getExtension().set(extension);
    task.getProjectAdapter().set(projectAdapter);
    task.getProjectName().set(project.getName());
    task.getProjectVersion().set(project.getVersion().toString());
    task.getReportFile().set(
        project.getLayout().getBuildDirectory()
            .file("reports/refactor-first/refactor-first-report.csv")
    );
});

// Register JSON report task
project.getTasks().register("refactorFirstJsonReport", JsonReportTask.class, task -> {
    task.getExtension().set(extension);
    task.getProjectAdapter().set(projectAdapter);
    task.getProjectName().set(project.getName());
    task.getProjectVersion().set(project.getVersion().toString());
    task.getReportFile().set(
        project.getLayout().getBuildDirectory()
            .file("reports/refactor-first/refactor-first-report.json")
    );
});
```

**Step 3: Refactor if needed**

### Phase 4: Testing

#### Task 4.1: Create Test Fixtures
**Directory**: `../refactor-first-gradle-plugin/src/test/fixtures`

Create test fixture projects:

```
refactor-first-gradle-plugin/src/test/fixtures/
├── simple-java-project/
│   ├── build.gradle
│   └── src/main/java/com/example/SimpleClass.java
├── simple-kotlin-project/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/example/SimpleClass.kt
└── multi-module-java/
    ├── settings.gradle
    ├── build.gradle
    ├── module1/build.gradle
    └── module1/src/main/java/com/example/Module1Class.java
```

#### Task 4.2: Create Integration Tests
**File**: `refactor-first-gradle-plugin/src/test/java/org/hjug/gradlereport/RefactorFirstPluginIntegrationTest.java`

```java
package org.hjug.gradlereport;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RefactorFirstPluginIntegrationTest {

    @Test
    void generatesHtmlReportForSimpleJavaProject(@TempDir Path tempDir) {
        // Copy simple-java-project fixture to tempDir
        // Run Gradle task
        GradleRunner runner = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withPluginClasspath()
            .withArguments("refactorFirstHtmlReport")
            .build();

        // Verify report was generated
        File reportFile = tempDir.resolve("build/reports/refactor-first/refactor-first-report.html").toFile();
        assertTrue(reportFile.exists(), "HTML report should be generated");
    }

    @Test
    void respectsExcludeTestsConfiguration(@TempDir Path tempDir) {
        // Test with excludeTests = true
        // Verify test classes are not analyzed
    }

    @Test
    void handlesMultiModuleProjects(@TempDir Path tempDir) {
        // Test with multi-module fixture
        // Verify all modules are analyzed
    }
}
```

### Phase 5: Documentation

#### Task 5.1: Update Main README.md with Plugin Usage
**File**: `../README.md`

Add Gradle plugin usage section:

```markdown
## Gradle Plugin

### Installation

Add the plugin to your `build.gradle.kts`:

```kotlin
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'org.hjug.refactorfirst.plugin:refactor-first-gradle-plugin:0.11.0'
    }
}

apply plugin: 'org.hjug.refactorfirst'
```

### Configuration

```kotlin
refactorFirst {
    showDetails.set(false)
    backEdgeAnalysisCount.set(50)
    analyzeCycles.set(true)
    excludeTests.set(true)
    minifyHtml.set(false)
    outputDirectory.set(file("build/reports/refactor-first"))
}
```

### Usage

Generate reports:

```bash
./gradlew refactorFirstHtmlReport
./gradlew refactorFirstCsvReport
./gradlew refactorFirstJsonReport
```

### Java Version Support

- Supported: Java 11, 17, 21
- Java 25 support will be added in a future iteration
```

#### Task 5.2: Create Plugin-Specific README
**File**: `refactor-first-gradle-plugin/README.md`

Create comprehensive plugin documentation:

```markdown
# RefactorFirst Gradle Plugin

## Overview
Gradle plugin for RefactorFirst code analysis tool.

## Quick Start

1. Add plugin dependency to build.gradle.kts
2. Configure plugin extension
3. Run report generation tasks

## Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| showDetails | Boolean | false | Show detailed metrics |
| backEdgeAnalysisCount | Integer | 50 | Number of back edges to analyze (0 = all) |
| analyzeCycles | Boolean | true | Analyze circular dependencies |
| excludeTests | Boolean | true | Exclude test classes from analysis |
| minifyHtml | Boolean | false | Minify HTML output |
| testSourceDirectory | File | src/test/java | Custom test source directory |
| outputDirectory | File | build/reports/refactor-first | Report output directory |

## Migration from Maven Plugin

See main README for migration guide.

## Hybrid Build Architecture

This plugin uses a hybrid Maven-Gradle build system:
- Maven orchestrates the Gradle build via exec-maven-plugin
- Gradle handles plugin compilation and testing
- Dependencies are copied by Maven and consumed by Gradle

This approach maintains consistency with the overall RefactorFirst project structure.
```

#### Task 5.3: Update AGENTS.md
**File**: `../AGENTS.md`

Add Gradle plugin build commands:

```markdown
## Gradle Plugin Build Commands

# Build Gradle plugin via Maven
mvn clean install -pl refactor-first-gradle-plugin

# Build Gradle plugin directly via Gradle
cd refactor-first-gradle-plugin
./gradlew clean build

# Run Gradle plugin tests
./gradlew test
```

### Phase 6: CI/CD Integration

#### Task 6.1: Update GitHub Actions
**File**: `.github/workflows/build.yml`

Add Gradle plugin to existing CI pipeline:

```yaml
- name: Build Gradle Plugin
  run: mvn clean install -pl refactor-first-gradle-plugin

- name: Test Gradle Plugin
  run: cd refactor-first-gradle-plugin && ./gradlew test
```

#### Task 6.2: Configure Maven Central Publishing
**File**: `../refactor-first-gradle-plugin/build.gradle`

Add Maven Central publishing configuration (already included in Task 2.1)

## Implementation Order

Execute tasks in this order following TDD red-green-refactor cycle:

1. Task 1.1: Test and Implement RefactorFirstPluginException (Red-Green-Refactor)
2. Task 1.2: Test and Implement RefactorFirstExtension (Red-Green-Refactor)
3. Task 1.3: Test and Implement GradleProjectAdapter (Red-Green-Refactor)
4. Task 1.4: Test and Implement HtmlReportTask (Red-Green-Refactor)
5. Task 1.5: Test and Implement RefactorFirstPlugin (Red-Green-Refactor)
6. Task 2.1: Update build.gradle
7. Task 2.2: Update parent pom.xml
8. Task 2.3: Update Gradle plugin pom.xml
9. Task 3.1: Test and Implement CsvReportTask (Red-Green-Refactor)
10. Task 3.2: Test and Implement JsonReportTask (Red-Green-Refactor)
11. Task 3.3: Test and Update RefactorFirstPlugin with Additional Tasks (Red-Green-Refactor)
12. Task 4.1: Create Test Fixtures
13. Task 4.2: Create Integration Tests
14. Task 5.1: Update Main README.md with Plugin Usage
15. Task 5.2: Create Plugin-Specific README
16. Task 5.3: Update AGENTS.md
17. Task 6.1: Update GitHub Actions
18. Task 6.2: Configure Maven Central Publishing

## TDD Process for Each Task

For each numbered task in Phase 1 and Phase 3, follow this cycle:

1. **Red**: Write a failing test that defines the expected behavior
2. **Green**: Write the minimum implementation code to make the test pass
3. **Refactor**: Improve the code while keeping tests green
4. **Verify**: Run all tests to ensure nothing broke

This ensures that all production code is backed by tests and follows the TDD methodology.

## Validation

After implementation, verify:

- [ ] All unit tests pass (Phase 1 and Phase 3 tasks - TDD verification)
- [ ] All integration tests pass (Phase 4 tasks)
- [ ] Plugin can be applied to a Gradle project without errors
- [ ] All three report types (HTML, CSV, JSON) can be generated
- [ ] Configuration options work as expected
- [ ] Plugin works with Java 11, 17, and 21 projects
- [ ] Plugin works with Kotlin projects
- [ ] Manual testing on real projects succeeds
- [ ] Documentation is complete and accurate
- [ ] TDD cycle was followed for all code implementation tasks (Red-Green-Refactor)
- [ ] README.md updated with Gradle plugin usage instructions

## TDD Methodology Notes

This implementation plan follows strict Test-Driven Development (TDD) methodology:

- **Red Phase**: Write failing tests first to define expected behavior
- **Green Phase**: Write minimal implementation code to make tests pass
- **Refactor Phase**: Improve code while keeping tests green
- **Verification**: Run all tests after each cycle to ensure no regressions

All production code in Phase 1 (Core Plugin Implementation) and Phase 3 (Additional Task Implementation) must be developed using this TDD cycle. Configuration tasks (Phase 2) and documentation tasks (Phase 5) do not require TDD but should be validated through the existing test suite.

---

**END OF IMPLEMENTATION PLAN**
