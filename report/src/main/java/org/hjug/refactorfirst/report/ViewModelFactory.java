package org.hjug.refactorfirst.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hjug.refactorfirst.report.model.CycleBreakdownRowDTO;
import org.hjug.refactorfirst.report.model.DisharmonyChartDTO;
import org.hjug.refactorfirst.report.model.DisharmonySectionDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableRowDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;

/**
 * Translates a {@link RefactorFirstReportDTO} into the nested view model the Mustache templates
 * render against — the Java-side mirror of the page repo's {@code prepareReportData} contract.
 */
public class ViewModelFactory {

    /** Builds the view model for the given report DTO. */
    public Map<String, Object> createViewModel(RefactorFirstReportDTO report) {
        String repoUrl =
                report.getProject() == null ? null : report.getProject().getRepoUrl();
        Map<String, Object> project = new LinkedHashMap<>();
        if (report.getProject() != null) {
            project.put("name", report.getProject().getName());
            project.put("version", report.getProject().getVersion());
            project.put("repoUrl", report.getProject().getRepoUrl());
            project.put("baseDir", report.getProject().getBaseDir());
            project.put("scanTimestamp", report.getProject().getScanTimestamp());
            project.put("hasAnyDisharmony", report.getProject().isHasAnyDisharmony());
            project.put("analysisFailed", report.getProject().isAnalysisFailed());
        }

        Map<String, Object> classMap = new LinkedHashMap<>();
        if (report.getClassMap() != null) {
            classMap.put("graphId", report.getClassMap().getGraphId());
            classMap.put("classCount", report.getClassMap().getClassCount());
            classMap.put("relationshipCount", report.getClassMap().getRelationshipCount());
            classMap.put("dotThreshold", report.getClassMap().getDotThreshold());
            classMap.put("dotThresholdExceeded", report.getClassMap().isDotThresholdExceeded());
            classMap.put("hasEdges", report.getClassMap().isHasEdges());
        }

        Map<String, Object> classRelationshipsToRemove = new LinkedHashMap<>();
        if (report.getClassRelationshipsToRemove() != null) {
            classRelationshipsToRemove.put(
                    "cycleCount", report.getClassRelationshipsToRemove().getCycleCount());
            classRelationshipsToRemove.put(
                    "relationshipsToRemoveCount",
                    report.getClassRelationshipsToRemove().getRelationshipsToRemoveCount());
            classRelationshipsToRemove.put(
                    "hasRelationships", report.getClassRelationshipsToRemove().isHasRelationships());
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (report.getClassRelationshipsToRemove().getRelationships() != null) {
                for (org.hjug.refactorfirst.report.model.ClassRelationshipDTO relationship :
                        report.getClassRelationshipsToRemove().getRelationships()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sourceClass", relationship.getSourceClass());
                    row.put("targetClass", relationship.getTargetClass());
                    row.put("sourceClassPath", relationship.getSourceClassPath());
                    row.put("sourceClassUrl", ReportModelBuilder.sourceUrl(repoUrl, relationship.getSourceClassPath()));
                    row.put("targetClassPath", relationship.getTargetClassPath());
                    row.put("targetClassUrl", ReportModelBuilder.sourceUrl(repoUrl, relationship.getTargetClassPath()));
                    row.put("simpleSourceClassName", relationship.getSimpleSourceClassName());
                    row.put("simpleTargetClassName", relationship.getSimpleTargetClassName());
                    row.put("sourceMarked", relationship.isSourceMarked());
                    row.put("targetMarked", relationship.isTargetMarked());
                    row.put("weight", relationship.getWeight());
                    row.put("priority", relationship.getPriority());
                    row.put("cycleCount", relationship.getCycleCount());
                    row.put("effortRank", relationship.getEffortRank());
                    row.put("alsoRemovesPackageRelationship", relationship.isAlsoRemovesPackageRelationship());
                    row.put("packageCycleCount", relationship.getPackageCycleCount());
                    relationships.add(row);
                }
            }
            classRelationshipsToRemove.put("relationships", relationships);
        }

        Map<String, Object> packageMap = new LinkedHashMap<>();
        if (report.getPackageMap() != null) {
            packageMap.put("graphId", report.getPackageMap().getGraphId());
            packageMap.put("classCount", report.getPackageMap().getClassCount());
            packageMap.put("relationshipCount", report.getPackageMap().getRelationshipCount());
            packageMap.put("dotThreshold", report.getPackageMap().getDotThreshold());
            packageMap.put("dotThresholdExceeded", report.getPackageMap().isDotThresholdExceeded());
            packageMap.put("hasEdges", report.getPackageMap().isHasEdges());
        }

        Map<String, Object> packageRelationshipsToRemove = new LinkedHashMap<>();
        if (report.getPackageRelationshipsToRemove() != null) {
            packageRelationshipsToRemove.put(
                    "cycleCount", report.getPackageRelationshipsToRemove().getCycleCount());
            packageRelationshipsToRemove.put(
                    "relationshipsToRemoveCount",
                    report.getPackageRelationshipsToRemove().getRelationshipsToRemoveCount());
            packageRelationshipsToRemove.put(
                    "hasRelationships", report.getPackageRelationshipsToRemove().isHasRelationships());
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (report.getPackageRelationshipsToRemove().getRelationships() != null) {
                for (org.hjug.refactorfirst.report.model.PackageRelationshipDTO relationship :
                        report.getPackageRelationshipsToRemove().getRelationships()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sourcePackage", relationship.getSourcePackage());
                    row.put("targetPackage", relationship.getTargetPackage());
                    row.put("sourcePackagePath", relationship.getSourcePackagePath());
                    row.put(
                            "sourcePackageUrl",
                            ReportModelBuilder.sourceUrl(repoUrl, relationship.getSourcePackagePath()));
                    row.put("targetPackagePath", relationship.getTargetPackagePath());
                    row.put(
                            "targetPackageUrl",
                            ReportModelBuilder.sourceUrl(repoUrl, relationship.getTargetPackagePath()));
                    row.put("sourceMarked", relationship.isSourceMarked());
                    row.put("targetMarked", relationship.isTargetMarked());
                    row.put("weight", relationship.getWeight());
                    row.put("priority", relationship.getPriority());
                    row.put("cycleCount", relationship.getCycleCount());
                    row.put("effortRank", relationship.getEffortRank());
                    List<Map<String, Object>> brokenClassRelationships = new ArrayList<>();
                    if (relationship.getClassRelationshipsToBreakPackage() != null) {
                        for (org.hjug.refactorfirst.report.model.ClassRelationshipDTO classRelationship :
                                relationship.getClassRelationshipsToBreakPackage()) {
                            Map<String, Object> brokenClassRelationship = new LinkedHashMap<>();
                            brokenClassRelationship.put("sourceClass", classRelationship.getSourceClass());
                            brokenClassRelationship.put("targetClass", classRelationship.getTargetClass());
                            brokenClassRelationship.put("sourceClassPath", classRelationship.getSourceClassPath());
                            brokenClassRelationship.put(
                                    "sourceClassUrl",
                                    ReportModelBuilder.sourceUrl(repoUrl, classRelationship.getSourceClassPath()));
                            brokenClassRelationship.put("targetClassPath", classRelationship.getTargetClassPath());
                            brokenClassRelationship.put(
                                    "targetClassUrl",
                                    ReportModelBuilder.sourceUrl(repoUrl, classRelationship.getTargetClassPath()));
                            brokenClassRelationship.put(
                                    "simpleSourceClassName", classRelationship.getSimpleSourceClassName());
                            brokenClassRelationship.put(
                                    "simpleTargetClassName", classRelationship.getSimpleTargetClassName());
                            brokenClassRelationship.put("sourceMarked", classRelationship.isSourceMarked());
                            brokenClassRelationship.put("targetMarked", classRelationship.isTargetMarked());
                            brokenClassRelationship.put("weight", classRelationship.getWeight());
                            brokenClassRelationships.add(brokenClassRelationship);
                        }
                    }
                    row.put("classRelationshipsToBreakPackage", brokenClassRelationships);
                    relationships.add(row);
                }
            }
            packageRelationshipsToRemove.put("relationships", relationships);
        }

        List<Map<String, Object>> disharmonies = new ArrayList<>();
        if (report.getDisharmonies() != null) {
            for (DisharmonySectionDTO section : report.getDisharmonies()) {
                Map<String, Object> disharmony = new LinkedHashMap<>();
                disharmony.put("type", section.getType());
                disharmony.put("anchorId", section.getAnchorId());
                disharmony.put("title", section.getTitle());
                disharmony.put("methodLevel", section.isMethodLevel());
                disharmony.put("problem", section.getProblem());
                disharmony.put("solution", section.getSolution());
                disharmony.put("maxPriority", section.getMaxPriority());

                DisharmonyChartDTO chart = section.getChart();
                Map<String, Object> chartView = new LinkedHashMap<>();
                chartView.put("canvasId", chart != null ? chart.getCanvasId() : null);
                chartView.put("xaxisLabel", chart != null ? chart.getXAxisLabel() : null);
                chartView.put("yaxisLabel", chart != null ? chart.getYAxisLabel() : null);
                List<Map<String, Object>> bubbles = new ArrayList<>();
                if (chart != null && chart.getBubbles() != null) {
                    for (org.hjug.refactorfirst.report.model.ChartJsBubbleDTO bubble : chart.getBubbles()) {
                        Map<String, Object> bubbleView = new LinkedHashMap<>();
                        bubbleView.put("id", bubble.getId());
                        bubbleView.put("label", bubble.getLabel());
                        bubbleView.put("x", bubble.getX());
                        bubbleView.put("y", bubble.getY());
                        bubbleView.put("r", bubble.getR());
                        bubbleView.put("priority", bubble.getPriority());
                        bubbleView.put("effortRank", bubble.getEffortRank());
                        bubbleView.put("changePronenessRank", bubble.getChangePronenessRank());
                        bubbleView.put("color", bubble.getColor());
                        bubbleView.put("borderColor", bubble.getBorderColor());
                        bubbleView.put("path", bubble.getPath());
                        bubbleView.put("sourceUrl", ReportModelBuilder.sourceUrl(repoUrl, bubble.getPath()));
                        bubbles.add(bubbleView);
                    }
                }
                chartView.put("bubbles", bubbles);
                disharmony.put("chart", chartView);

                DisharmonyTableDTO table = section.getTable();
                Map<String, Object> tableView = new LinkedHashMap<>();
                tableView.put(
                        "headers",
                        table != null && table.getHeaders() != null ? table.getHeaders() : new ArrayList<>());
                List<Map<String, Object>> rows = new ArrayList<>();
                if (table != null && table.getRows() != null) {
                    for (DisharmonyTableRowDTO row : table.getRows()) {
                        Map<String, Object> rowView = new LinkedHashMap<>();
                        List<Map<String, Object>> cells = new ArrayList<>();
                        if (row.getCells() != null) {
                            for (org.hjug.refactorfirst.report.model.DisharmonyTableCellDTO cell : row.getCells()) {
                                Map<String, Object> cellView = new LinkedHashMap<>();
                                cellView.put("content", cell.getContent());
                                cellView.put("align", cell.getAlign());
                                cellView.put("path", cell.getPath());
                                cellView.put("sourceUrl", ReportModelBuilder.sourceUrl(repoUrl, cell.getPath()));
                                cells.add(cellView);
                            }
                        }
                        rowView.put("cells", cells);
                        rows.add(rowView);
                    }
                }
                tableView.put("rows", rows);
                disharmony.put("table", tableView);

                disharmony.put("solution", section.getSolution());
                disharmonies.add(disharmony);
            }
        }

        Map<String, Object> classCycles = new LinkedHashMap<>();
        if (report.getClassCycles() != null) {
            classCycles.put("hasCycles", report.getClassCycles().isHasCycles());
            List<Map<String, Object>> summary = new ArrayList<>();
            if (report.getClassCycles().getSummary() != null) {
                for (org.hjug.refactorfirst.report.model.CycleSummaryDTO cycleSummary :
                        report.getClassCycles().getSummary()) {
                    Map<String, Object> cycle = new LinkedHashMap<>();
                    cycle.put("cycleName", cycleSummary.getCycleName());
                    cycle.put("priority", cycleSummary.getPriority());
                    cycle.put("classCount", cycleSummary.getClassCount());
                    cycle.put("relationshipCount", cycleSummary.getRelationshipCount());
                    summary.add(cycle);
                }
            }
            classCycles.put("summary", summary);

            Map<String, Object> largestCycle = new LinkedHashMap<>();
            org.hjug.refactorfirst.report.model.LargestCycleDTO largestCycleDto =
                    report.getClassCycles().getLargestCycle();
            if (largestCycleDto != null) {
                largestCycle.put("hasCycleMap", largestCycleDto.isHasCycleMap());
                largestCycle.put("cycleName", largestCycleDto.getCycleName());
                largestCycle.put("cycleIdentifier", largestCycleDto.getCycleIdentifier());
                largestCycle.put("classCount", largestCycleDto.getClassCount());
                largestCycle.put("relationshipCount", largestCycleDto.getRelationshipCount());
                largestCycle.put("dotThresholdExceeded", largestCycleDto.isDotThresholdExceeded());
                List<Map<String, Object>> breakdown = new ArrayList<>();
                if (largestCycleDto.getBreakdown() != null) {
                    for (CycleBreakdownRowDTO row : largestCycleDto.getBreakdown()) {
                        Map<String, Object> rowView = new LinkedHashMap<>();
                        rowView.put("className", row.getClassName());
                        rowView.put("classPath", row.getClassPath());
                        rowView.put("classUrl", ReportModelBuilder.sourceUrl(repoUrl, row.getClassPath()));
                        rowView.put("marked", row.isMarked());
                        rowView.put("edgesHtml", row.getEdgesHtml());
                        breakdown.add(rowView);
                    }
                }
                largestCycle.put("breakdown", breakdown);
            }
            classCycles.put("largestCycle", largestCycle);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project);
        view.put("classMap", classMap);
        view.put("classRelationshipsToRemove", classRelationshipsToRemove);
        view.put("packageMap", packageMap);
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove);
        view.put("hasDisharmonies", report.isHasDisharmonies());
        view.put("disharmonies", disharmonies);
        view.put("classCycles", classCycles);
        return view;
    }
}
