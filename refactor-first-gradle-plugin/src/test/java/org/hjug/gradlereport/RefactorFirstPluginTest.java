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

        RefactorFirstExtension extension = project.getExtensions().getByType(RefactorFirstExtension.class);

        assertEquals(false, extension.getShowDetails().get());
        assertEquals(50, extension.getBackEdgeAnalysisCount().get());
    }

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
}
