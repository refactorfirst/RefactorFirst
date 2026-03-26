package org.hjug.graphbuilder.metrics.testclasses;

public class RefusedBequestExample extends BaseService {

    private String customData;
    private int customValue;

    public void doCustomWork() {
        customData = "custom";
        customValue = 42;
    }

    public void processData() {
        customData = customData + "_processed";
    }

    public String getCustomData() {
        return customData;
    }

    public int getCustomValue() {
        return customValue;
    }
}
