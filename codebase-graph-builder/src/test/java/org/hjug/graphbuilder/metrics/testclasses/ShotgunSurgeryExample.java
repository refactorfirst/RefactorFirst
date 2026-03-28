package org.hjug.graphbuilder.metrics.testclasses;

/**
 * Example class exhibiting Shotgun Surgery disharmony.
 * Per Lanza & Marinescu "Object-Oriented Metrics in Practice" Fig. 6.14:
 * CM > SHORT_MEMORY_CAP(7) AND CC > MANY(7)
 *
 * performService() is called by 8 distinct methods in 8 distinct caller classes
 * (ShotgunCaller1..ShotgunCaller8), so:
 *   CM = 8 > SHORT_MEMORY_CAP(7)
 *   CC = 8 > MANY(7)
 */
public class ShotgunSurgeryExample {

    private String result;

    public String performService(String input) {
        result = input;
        return result;
    }
}
