package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HtmlReportTaskTest {
    private Project project;
    private HtmlReportTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks()
                .register("refactorFirstHtmlReport", HtmlReportTask.class)
                .get();
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

    @Test
    void taskHasBackEdgeAnalysisCountProperty() {
        assertNotNull(task.getBackEdgeAnalysisCount());
    }

    @Test
    void taskHasAnalyzeCyclesProperty() {
        assertNotNull(task.getAnalyzeCycles());
    }

    @Test
    void taskHasShowDetailsProperty() {
        assertNotNull(task.getShowDetails());
    }

    @Test
    void taskHasMinifyHtmlProperty() {
        assertNotNull(task.getMinifyHtml());
    }

    @Test
    void taskHasExcludeTestsProperty() {
        assertNotNull(task.getExcludeTests());
    }

    @Test
    void taskHasTestSourceDirectoryProperty() {
        assertNotNull(task.getTestSourceDirectory());
    }

    @Test
    void taskHasOutputDirectoryProperty() {
        assertNotNull(task.getOutputDirectory());
    }
}
