package org.hjug.graphbuilder.metrics.testclasses;

public class SignificantDuplicationIntraClass {

    public int methodA(int x) {
        int a = x + 1;
        int b = a * 2;
        int c = b - 3;
        int d = c / 4;
        int e = d + 5;
        int f = e * 6;
        int g = f + x;
        int h = g + 1;
        int i = h * 2;
        int j = i - 3;
        int k = j / 4;
        int l = k + 5;
        return l * 6;
    }

    public int methodB(int x) {
        int a = x + 1;
        int b = a * 2;
        int c = b - 3;
        int d = c / 4;
        int e = d + 5;
        int f = e * 6;
        int g = f - x;
        int h = g + 1;
        int i = h * 2;
        int j = i - 3;
        int k = j / 4;
        int l = k + 5;
        return l * 6;
    }
}
