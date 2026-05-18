package org.hjug.metrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

/**
 * Created by Jim on 11/16/2016.
 */
@Data
public class GodClass implements Disharmony {

    private String className;
    private String fileRepoPath;
    private String packageName;
    private Integer wmc;
    private Integer atfd;
    private Float tcc;

    private Integer wmcRank;
    private Integer atfdRank;
    private Integer tccRank;
    private Integer sumOfRanks;
    private Integer overallRank;

    // Regex to capture digits for ATFD/WMC and decimal for TCC
    Pattern pattern = Pattern.compile("ATFD=(\\d+), WMC=(\\d+), TCC=([\\d.]+)");

    public GodClass(String className, String fileRepoPath, String packageName, String result) {
        this.className = className;
        this.fileRepoPath = fileRepoPath.replace("\\", "/");
        this.packageName = packageName;

        // Regex to capture digits for ATFD/WMC and decimal for TCC
        Matcher matcher = pattern.matcher(result);

        if (matcher.find()) {
            atfd = Integer.parseInt(matcher.group(1));
            wmc = Integer.parseInt(matcher.group(2));
            tcc = Float.parseFloat(matcher.group(3));
        }
    }
}
