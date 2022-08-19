package org.hjug.metrics;

import java.util.Scanner;
import lombok.Data;

/**
 * Created by Jim on 11/16/2016.
 */
@Data
public class CBOClass {

    private String className;
    private String fileName;
    private String packageName;

    private Integer couplingCount;

    public CBOClass(String className, String fileName, String packageName, String result) {
        this.className = className;
        this.fileName = fileName;
        this.packageName = packageName;

        try (Scanner scanner = new Scanner(result)) {
            couplingCount = scanner.useDelimiter("[^\\d]+").nextInt();
        }
    }
}
