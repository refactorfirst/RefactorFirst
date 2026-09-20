package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvReportTaskTest {
    private Project project;
    private CsvReportTask task;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks()
                .register("refactorFirstCsvReport", CsvReportTask.class)
                .get();
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
    void taskHasShowDetailsProperty() {
        assertNotNull(task.getShowDetails());
    }

    @Test
    void taskHasOutputDirectoryProperty() {
        assertNotNull(task.getOutputDirectory());
    }
}
