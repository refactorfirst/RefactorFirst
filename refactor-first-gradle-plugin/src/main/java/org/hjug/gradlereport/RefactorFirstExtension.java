package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class RefactorFirstExtension {
    private final Property<Boolean> showDetails;
    private final Property<Integer> backEdgeAnalysisCount;
    private final Property<Boolean> analyzeCycles;
    private final Property<Boolean> excludeTests;
    private final Property<Boolean> minifyHtml;
    private final Property<File> testSourceDirectory;
    private final Property<File> outputDirectory;

    public RefactorFirstExtension(ObjectFactory objects) {
        this.showDetails = objects.property(Boolean.class).convention(false);
        this.backEdgeAnalysisCount = objects.property(Integer.class).convention(50);
        this.analyzeCycles = objects.property(Boolean.class).convention(true);
        this.excludeTests = objects.property(Boolean.class).convention(true);
        this.minifyHtml = objects.property(Boolean.class).convention(false);
        this.testSourceDirectory = objects.property(File.class);
        this.outputDirectory = objects.property(File.class).convention(new File("build/reports/refactor-first"));
    }

    public Property<Boolean> getShowDetails() {
        return showDetails;
    }

    public Property<Integer> getBackEdgeAnalysisCount() {
        return backEdgeAnalysisCount;
    }

    public Property<Boolean> getAnalyzeCycles() {
        return analyzeCycles;
    }

    public Property<Boolean> getExcludeTests() {
        return excludeTests;
    }

    public Property<Boolean> getMinifyHtml() {
        return minifyHtml;
    }

    public Property<File> getTestSourceDirectory() {
        return testSourceDirectory;
    }

    public Property<File> getOutputDirectory() {
        return outputDirectory;
    }

    public boolean isShowDetails() {
        return getShowDetails().get();
    }

    public void setShowDetails(boolean showDetails) {
        getShowDetails().set(showDetails);
    }

    public int getBackEdgeAnalysisCountValue() {
        return getBackEdgeAnalysisCount().get();
    }

    public void setBackEdgeAnalysisCount(int count) {
        getBackEdgeAnalysisCount().set(count);
    }

    public boolean isAnalyzeCycles() {
        return getAnalyzeCycles().get();
    }

    public void setAnalyzeCycles(boolean analyzeCycles) {
        getAnalyzeCycles().set(analyzeCycles);
    }

    public boolean isExcludeTests() {
        return getExcludeTests().get();
    }

    public void setExcludeTests(boolean excludeTests) {
        getExcludeTests().set(excludeTests);
    }

    public boolean isMinifyHtml() {
        return getMinifyHtml().get();
    }

    public void setMinifyHtml(boolean minifyHtml) {
        getMinifyHtml().set(minifyHtml);
    }

    public void setTestSourceDirectory(File testSourceDirectory) {
        getTestSourceDirectory().set(testSourceDirectory);
    }

    public void setOutputDirectory(File outputDirectory) {
        getOutputDirectory().set(outputDirectory);
    }

    public void validate() {
        if (getBackEdgeAnalysisCount().get() < 0) {
            throw new RefactorFirstPluginException("backEdgeAnalysisCount must be >= 0, got: "
                    + getBackEdgeAnalysisCount().get());
        }
    }
}
