package org.hjug.graphbuilder.metrics.testclasses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GodClassExample {

    private List<String> data1 = new ArrayList<>();
    private Map<String, String> data2 = new HashMap<>();
    private StringBuilder builder = new StringBuilder();
    private ExternalService service1;
    private AnotherService service2;
    private ThirdService service3;
    private FourthService service4;
    private FifthService service5;
    private SixthService service6;

    public void method1() {
        service1.doSomething();
        service2.process();
        data1.add("test");
    }

    public void method2() {
        service3.execute();
        service4.run();
        data2.put("key", "value");
    }

    public void method3() {
        service5.handle();
        service6.perform();
        builder.append("data");
    }

    public void method4() {
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                service1.doSomething();
            } else {
                service2.process();
            }
        }
    }

    public void method5() {
        service3.execute();
        service4.run();
    }

    public void method6() {
        service5.handle();
        service6.perform();
    }

    public void method7() {
        data1.clear();
        data2.clear();
    }

    public void method8() {
        builder.setLength(0);
    }

    public void method9() {
        service1.doSomething();
    }

    public void method10() {
        service2.process();
    }

    static class ExternalService {
        void doSomething() {}
    }

    static class AnotherService {
        void process() {}
    }

    static class ThirdService {
        void execute() {}
    }

    static class FourthService {
        void run() {}
    }

    static class FifthService {
        void handle() {}
    }

    static class SixthService {
        void perform() {}
    }
}
