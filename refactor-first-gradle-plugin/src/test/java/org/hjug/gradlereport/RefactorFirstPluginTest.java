package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefactorFirstPluginTest {
    private Project project;

    /** Creates a Gradle project for each test. */
    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
    }

    /** Verifies applying the plugin creates its extension. */
    @Test
    void pluginCreatesExtension() {
        new RefactorFirstPlugin().apply(project);

        assertNotNull(project.getExtensions().findByName("refactorFirst"));
    }

    /** Verifies applying the plugin registers the HTML task. */
    @Test
    void pluginRegistersHtmlReportTask() {
        new RefactorFirstPlugin().apply(project);

        TaskProvider<?> task = project.getTasks().named("refactorFirstHtmlReport");
        assertNotNull(task);
    }

    /** Verifies the registered extension uses its defaults. */
    @Test
    void extensionHasDefaultValues() {
        new RefactorFirstPlugin().apply(project);

        RefactorFirstExtension extension = project.getExtensions().getByType(RefactorFirstExtension.class);

        assertEquals(false, extension.getShowDetails().get());
        assertEquals(50, extension.getBackEdgeAnalysisCount().get());
    }

    /** Verifies applying the plugin registers the CSV task. */
    @Test
    void pluginRegistersCsvReportTask() {
        new RefactorFirstPlugin().apply(project);

        TaskProvider<?> task = project.getTasks().named("refactorFirstCsvReport");
        assertNotNull(task);
    }

    /** Verifies applying the plugin registers the JSON task. */
    @Test
    void pluginRegistersJsonReportTask() {
        new RefactorFirstPlugin().apply(project);

        TaskProvider<?> task = project.getTasks().named("refactorFirstJsonReport");
        assertNotNull(task);
    }
}
