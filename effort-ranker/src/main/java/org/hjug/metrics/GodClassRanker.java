package org.hjug.metrics;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjIntConsumer;

/**
 * Created by Wendy on 11/16/2016.
 */
public class GodClassRanker {

    public void rankGodClasses(List<GodClass> godClasses) {
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
        godClasses.sort(Comparator.comparing(GodClass::getWmc));

        Function<GodClass, Integer> getWmc = GodClass::getWmc;
        ObjIntConsumer<GodClass> setWmcRank = GodClass::setWmcRank;

        setRank(godClasses, getWmc, setWmcRank);
    }

    void rankAtfd(List<GodClass> godClasses) {
        godClasses.sort(Comparator.comparing(GodClass::getAtfd));

        Function<GodClass, Integer> getAtfd = GodClass::getAtfd;
        ObjIntConsumer<GodClass> setAtfdRank = GodClass::setAtfdRank;

        setRank(godClasses, getAtfd, setAtfdRank);
    }

    void rankTcc(List<GodClass> godClasses) {
        godClasses.sort(Comparator.comparing(GodClass::getTcc));

        Function<GodClass, Float> getTcc = GodClass::getTcc;
        ObjIntConsumer<GodClass> setTccRank = GodClass::setTccRank;

        setRank(godClasses, getTcc, setTccRank);
    }

    <T extends Number & Comparable<? super T>> void setRank(List<GodClass> godClasses,
                                                                   Function<GodClass, T> getter, ObjIntConsumer<GodClass> setter) {
        int rank = 1;
        T prevousValue = null;

        for (GodClass godClass : godClasses) {
            T value = getter.apply(godClass);
            if (null == prevousValue) {
                prevousValue = value;
            }

            if (value.compareTo(prevousValue) > 0) {
                setter.accept(godClass, ++rank);
                prevousValue = value;
            } else {
                setter.accept(godClass, rank);
            }
        }
    }


}
