package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonReportTaskTest {
    private Project project;
    private JsonReportTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks()
                .register("refactorFirstJsonReport", JsonReportTask.class)
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
    void taskHasOutputDirectoryProperty() {
        assertNotNull(task.getOutputDirectory());
    }
}
