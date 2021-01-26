package org.hjug.metrics;

import lombok.Data;

/**
 * Created by Jim on 11/16/2016.
 */
@Data
public class GodClass {

    private String fileName;
    private String packageName;
    private Integer wmc;
    private Integer atfd;
    private Float tcc;

    private Integer wmcRank;
    private Integer atfdRank;
    private Integer tccRank;
    private Integer sumOfRanks;
    private Integer overallRank;

    public GodClass(String fileName, String packageName, String result) {
        this.fileName = fileName;
        this.packageName = packageName;

        //null (WMC=79, ATFD=79, TCC=0.027777777777777776)
        String [] values = result.substring(result.indexOf("(") + 1, result.indexOf(")")).split(", ");
        wmc = Integer.valueOf(values[0].split("=")[1]);
        atfd = Integer.valueOf(values[1].split("=")[1]);
        String rawTcc = values[2].split("=")[1];
        tcc = Float.valueOf(rawTcc.replace("%", ""));
    }
}
