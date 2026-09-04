package org.hjug.mavenreport;

import java.io.File;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.hjug.refactorfirst.report.ReportWriter;

final class MavenReportOutputDirectory {

    static String resolve(MavenProject project, File buildDirectory) {
        Reporting reporting = project.getModel().getReporting();
        String configuredDirectory = reporting == null ? null : reporting.getOutputDirectory();
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            File effectiveBuildDirectory = buildDirectory;
            if (effectiveBuildDirectory == null
                    && project.getBuild() != null
                    && project.getBuild().getDirectory() != null
                    && !project.getBuild().getDirectory().isBlank()) {
                effectiveBuildDirectory = new File(project.getBuild().getDirectory());
            }
            if (effectiveBuildDirectory == null) {
                effectiveBuildDirectory = new File(project.getBasedir(), "target");
            }
            configuredDirectory = new File(effectiveBuildDirectory, "site").getPath();
        }
        return ReportWriter.containReportDirectory(project.getBasedir(), configuredDirectory);
    }

    private MavenReportOutputDirectory() {}
}
