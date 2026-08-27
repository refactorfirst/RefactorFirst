package org.hjug.graphbuilder.metrics.testclasses;

public class SignificantDuplicationCrossClassB {

    public int computeResult(int x) {
        int p = x + 2;
        int q = p * 3;
        int r = q - 4;
        int s = r / 5;
        int t = s + 6;
        int u = t * 7;
        int v = u - x;
        int w = v + 2;
        int aa = w * 3;
        int bb = aa - 4;
        int cc = bb / 5;
        int dd = cc + 6;
        int ee = dd * 7;
        return ee;
    }
}
