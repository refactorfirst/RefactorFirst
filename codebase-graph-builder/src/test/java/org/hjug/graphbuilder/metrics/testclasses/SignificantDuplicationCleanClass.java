package org.hjug.graphbuilder.metrics.testclasses;

public class SignificantDuplicationCleanClass {

    public int hashA(int x) {
        int result = x * 31337;
        result ^= 0x1A2B3C4D;
        return result;
    }

    public int hashB(int x) {
        int result = x * 98765;
        result ^= 0x5E6F7A8B;
        return result;
    }
}
