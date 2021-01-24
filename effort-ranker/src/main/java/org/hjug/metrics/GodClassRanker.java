package org.hjug.metrics;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Wendy on 11/16/2016.
 */
@Slf4j
public class GodClassRanker {

    public void rankGodClasses(List<GodClass> godClasses) {
        log.info("Initiate ranking of God Classes");
        rankWmc(godClasses);
        rankAtfd(godClasses);
        rankTcc(godClasses);
        computeOverallRank(godClasses);
    }

    void computeOverallRank(List<GodClass> godClasses) {

        godClasses.forEach(godClass ->
                godClass.setSumOfRanks(godClass.getWmcRank() + godClass.getAtfdRank() + godClass.getTccRank()));

        godClasses.sort(Comparator.comparing(GodClass::getSumOfRanks));

        Function<GodClass, Integer> getSumOfRanks = GodClass::getSumOfRanks;
        ObjIntConsumer<GodClass> setOverallRank = GodClass::setOverallRank;

        setRank(godClasses, getSumOfRanks, setOverallRank);
    }

    void rankWmc(List<GodClass> godClasses) {
        log.info("Ranking Weighted Method per Class");
        godClasses.sort(Comparator.comparing(GodClass::getWmc));

        Function<GodClass, Integer> getWmc = GodClass::getWmc;
        ObjIntConsumer<GodClass> setWmcRank = GodClass::setWmcRank;

        setRank(godClasses, getWmc, setWmcRank);
    }

    void rankAtfd(List<GodClass> godClasses) {
        log.info("Ranking Access to Foreign Data");
        godClasses.sort(Comparator.comparing(GodClass::getAtfd));

        Function<GodClass, Integer> getAtfd = GodClass::getAtfd;
        ObjIntConsumer<GodClass> setAtfdRank = GodClass::setAtfdRank;

        setRank(godClasses, getAtfd, setAtfdRank);
    }

    void rankTcc(List<GodClass> godClasses) {
        log.info("Ranking Total Cyclomatic Complexity");
        godClasses.sort(Comparator.comparing(GodClass::getTcc));

        Function<GodClass, Float> getTcc = GodClass::getTcc;
        ObjIntConsumer<GodClass> setTccRank = GodClass::setTccRank;

        setRank(godClasses, getTcc, setTccRank);
    }

    <T extends Number & Comparable<? super T>> void setRank(List<GodClass> godClasses,
                                                                   Function<GodClass, T> getter, ObjIntConsumer<GodClass> setter) {
        int rank = 1;
        T previousValue = null;

        for (GodClass godClass : godClasses) {
            T value = getter.apply(godClass);
            if (null == previousValue) {
                previousValue = value;
            }

            if (value.compareTo(previousValue) > 0) {
                setter.accept(godClass, ++rank);
                previousValue = value;
            } else {
                setter.accept(godClass, rank);
            }
        }
    }


}
