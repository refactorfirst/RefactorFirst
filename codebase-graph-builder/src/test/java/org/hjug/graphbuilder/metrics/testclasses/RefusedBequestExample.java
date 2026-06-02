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

    // CC=3 (if + else-if + else)
    public String evaluateStatus(int value) {
        if (value > 100) {
            customData = "high:" + value;
            return "high";
        } else if (value > 50) {
            customData = "mid:" + value;
            return "mid";
        } else {
            customData = "low:" + value;
            return "low";
        }
    }

    // CC=3 (for + if)
    public int countItems(int[] values) {
        int count = 0;
        for (int v : values) {
            if (v > 0) {
                count++;
                customValue += v;
            }
        }
        return count;
    }

    // CC=5 (if + 3 else-if + else)
    public String processCustomData(String input, int mode) {
        if (input == null) {
            return "";
        } else if (mode == 1) {
            customData = input.toUpperCase();
            return customData;
        } else if (mode == 2) {
            customData = input.toLowerCase();
            customValue = input.length();
            return customData;
        } else if (mode == 3) {
            customValue = input.length();
            customData = input.trim();
            return customData;
        } else {
            return input;
        }
    }

    // CC=2 (if + else) — brings NOM to 8 (> NOM_AVERAGE=7)
    public boolean validateData(String input) {
        if (input == null || input.isEmpty()) {
            customData = "invalid";
            return false;
        } else {
            customData = input;
            return true;
        }
    }
}
