package org.hjug.mavenreport.RefactorFirstJsonReport;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import lombok.Builder;
import lombok.Data;
import org.hjug.cbc.RankedDisharmony;

@Data
@Builder
class JsonReportDisharmonyEntry {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private final String fileName;

    private final String className;

    private final String fullFilePath;

    private final Integer effortRank;

    private final Integer changePronenessRank;

    private final Integer priority;

    private final Integer weightedMethodCount;

    private final Integer commitCount;

    private final String mostRecentCommitTime;

    public static JsonReportDisharmonyEntry fromRankedDisharmony(RankedDisharmony entry) {
        return JsonReportDisharmonyEntry.builder()
                .fileName(entry.getFileName())
                .className(entry.getClassName())
                .effortRank(entry.getEffortRank())
                .changePronenessRank(entry.getChangePronenessRank())
                .priority(entry.getPriority())
                .weightedMethodCount(entry.getWmc())
                .commitCount(entry.getCommitCount())
                .mostRecentCommitTime(formatter.format(entry.getMostRecentCommitTime()))
                .fullFilePath(entry.getPath())
                .build();
    }
}
