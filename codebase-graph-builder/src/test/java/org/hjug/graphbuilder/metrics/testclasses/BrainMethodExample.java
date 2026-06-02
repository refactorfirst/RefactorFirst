package org.hjug.graphbuilder.metrics.testclasses;

import java.util.ArrayList;
import java.util.List;

public class BrainMethodExample {

    private List<String> items = new ArrayList<>();
    private int counter = 0;
    private String status = "";
    private boolean flag = false;

    public void brainMethod(int param1, String param2, boolean param3) {
        int localVar1 = 0;
        int localVar2 = 0;
        int localVar3 = 0;
        String localVar4 = "";
        String localVar5 = "";
        String localVar6 = "";
        String localVar7 = "";
        String localVar8 = "";

        if (param1 > 0) {
            if (param2 != null) {
                if (param3) {
                    for (int i = 0; i < param1; i++) {
                        if (i % 2 == 0) {
                            if (items.size() > 0) {
                                localVar1 = items.size();
                                localVar2 = counter;
                                localVar3 = localVar1 + localVar2;
                                localVar4 = items.get(0);
                                localVar5 = status;
                                localVar6 = param2;
                                localVar7 = localVar4 + localVar5;
                                localVar8 = localVar6 + localVar7;
                                items.add(localVar8);
                                counter++;
                            } else {
                                localVar1 = 0;
                                localVar2 = 0;
                                localVar3 = 0;
                                localVar4 = "";
                                localVar5 = "";
                                localVar6 = "";
                                localVar7 = "";
                                localVar8 = "";
                            }
                        } else {
                            if (flag) {
                                localVar1 = counter;
                                localVar2 = param1;
                                localVar3 = localVar1 * localVar2;
                                localVar4 = String.valueOf(localVar3);
                                localVar5 = status;
                                localVar6 = param2;
                                localVar7 = localVar4 + localVar5;
                                localVar8 = localVar6 + localVar7;
                                status = localVar8;
                            }
                        }
                    }
                } else {
                    localVar1 = counter;
                    localVar2 = param1;
                    localVar3 = localVar1 + localVar2;
                    localVar4 = String.valueOf(localVar3);
                    localVar5 = status;
                    localVar6 = param2;
                    localVar7 = localVar4 + localVar5;
                    localVar8 = localVar6 + localVar7;
                }
            } else {
                localVar1 = 0;
                localVar2 = 0;
                localVar3 = 0;
                localVar4 = "";
                localVar5 = "";
                localVar6 = "";
                localVar7 = "";
                localVar8 = "";
            }
        } else {
            localVar1 = counter;
            localVar2 = param1;
            localVar3 = localVar1 - localVar2;
            localVar4 = String.valueOf(localVar3);
            localVar5 = status;
            localVar6 = param2 != null ? param2 : "";
            localVar7 = localVar4 + localVar5;
            localVar8 = localVar6 + localVar7;
        }
    }

    public void simpleMethod() {
        items.add("simple");
    }
}
