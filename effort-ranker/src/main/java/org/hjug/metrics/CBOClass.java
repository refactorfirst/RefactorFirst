package org.hjug.metrics;

import java.util.Scanner;
import lombok.Data;

/**
 * Created by Jim on 11/16/2016.
 */
@Data
public class CBOClass implements Disharmony {

    private String className;
    private String fileRepoPath;
    private String packageName;

    private Integer couplingCount;

    public CBOClass(String className, String fileRepoPath, String packageName, String result) {
        this.className = className;
        this.fileRepoPath = fileRepoPath.replace("\\", "/");
        this.packageName = packageName;

        try (Scanner scanner = new Scanner(result)) {
            couplingCount = scanner.useDelimiter("[^\\d]+").nextInt();
        }
    }
}
