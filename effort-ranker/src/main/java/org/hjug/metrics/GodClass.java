package org.hjug.metrics;

import java.text.NumberFormat;
import java.text.ParseException;
import lombok.Data;

/**
 * Created by Jim on 11/16/2016.
 */
@Data
public class GodClass {

    private String className;
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

    public GodClass(String className, String fileName, String packageName, String result) {
        this.className = className;
        this.fileName = fileName;
        this.packageName = packageName;

        NumberFormat integerFormat = NumberFormat.getIntegerInstance();

        String[] values =
                result.substring(result.indexOf("(") + 1, result.indexOf(")")).split(", ");
        try {
            wmc = (int) (long) integerFormat.parse(extractValue(values[0]));
            atfd = (int) (long) integerFormat.parse(extractValue(values[1]));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        String rawTcc = extractValue(values[2]);
        tcc = Float.valueOf(rawTcc.replace("%", ""));
    }

    private String extractValue(String value) {
        return value.split("=")[1];
    }
}
