package org.hjug.graphbuilder.metrics.testclasses;

/**
 * Example class exhibiting Tradition Breaker disharmony.
 * Per Lanza & Marinescu "Object-Oriented Metrics in Practice" Fig. 7.9:
 *
 * Condition 1 — Excessive interface increase:
 *   NAS >= NOM_AVERAGE(7) AND PNAS >= TWO_THIRDS(0.67)
 *   Here: 3 overrides + 9 new = 12 total. NAS=9, PNAS=9/12=0.75
 *
 * Condition 2 — Child substantial size and complexity:
 *   NOM >= NOM_HIGH(12) AND (AMW > AVERAGE(2.0) OR WMC >= VERY_HIGH(47))
 *   Here: NOM=12, WMC≈26, AMW≈2.17 > 2.0
 *
 * Condition 3 — Parent non-dumb (BaseService):
 *   AMW > AVERAGE(2.0) AND NOM > NOM_HIGH/2(6) AND WMC >= VERY_HIGH/2(23)
 *   BaseService: NOM=10, WMC=23, AMW=2.3
 */
public class TraditionBreakerExample extends BaseService {

    private String feature1 = "";
    private int feature2 = 0;
    private boolean feature3 = false;
    private double feature4 = 0.0;
    private String feature5 = "";

    @Override
    protected void initialize() {
        serviceName = "TraditionBreaker";
    }

    @Override
    protected void configure(String config) {
        configuration = config + "_tb";
    }

    @Override
    protected void start() {
        isActive = true;
        feature1 = "started";
    }

    // 7 new services not present in parent:

    public String processFeature1(String input) {
        if (input == null) {
            feature1 = "";
            return "";
        }
        feature1 = input.trim();
        return feature1;
    }

    public int processFeature2(int value) {
        if (value > 0) {
            feature2 = value * 2;
        } else {
            feature2 = 0;
        }
        return feature2;
    }

    public boolean processFeature3(String key) {
        if (key != null) {
            if (!key.isEmpty()) {
                feature3 = true;
                feature1 = key;
            } else {
                feature3 = false;
            }
        } else {
            feature3 = false;
        }
        return feature3;
    }

    public double processFeature4(double amount) {
        if (amount > 0.0) {
            feature4 = amount * 1.1;
        } else {
            feature4 = 0.0;
        }
        return feature4;
    }

    public String processFeature5(String a, String b) {
        if (a != null) {
            if (b != null) {
                feature5 = a + ":" + b;
            } else {
                feature5 = a;
            }
        } else {
            feature5 = b != null ? b : "";
        }
        return feature5;
    }

    public int processFeature6(int x, int y) {
        if (x > y) {
            feature2 = x - y;
        } else if (x < y) {
            feature2 = y - x;
        } else {
            feature2 = 0;
        }
        return feature2;
    }

    public String getFeatureSummary() {
        return feature1 + ":" + feature2 + ":" + feature3 + ":" + feature4 + ":" + feature5;
    }

    // CC=3 — brings NOM to 11
    public String processFeature7(int count, String label) {
        if (count > 0) {
            feature1 = label + ":" + count;
        } else if (count < 0) {
            feature1 = label + ":negative";
        } else {
            feature1 = label + ":zero";
        }
        return feature1;
    }

    // CC=3 — brings NOM to 12, total WMC sufficient for AMW > 2.0
    public boolean processFeature8(String key, boolean flag) {
        if (key == null) {
            feature3 = false;
        } else if (flag) {
            feature3 = true;
        } else {
            feature3 = false;
        }
        return feature3;
    }
}
