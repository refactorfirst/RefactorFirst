package org.hjug.graphbuilder.metrics.testclasses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrainClassExample {

    private List<String> dataList = new ArrayList<>();
    private Map<String, Integer> dataMap = new HashMap<>();
    private int counter = 0;
    private String status = "";
    private boolean flag = false;

    private String method1Result = "";
    private int method1Counter = 0;

    private String method2Result = "";
    private int method2Total = 0;

    private String method3Result = "";
    private int method3Value = 0;

    public void complexMethod1(int param1, String param2, boolean param3) {
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
                            if (dataList.size() > 0) {
                                localVar1 = dataList.size();
                                localVar2 = counter;
                                localVar3 = localVar1 + localVar2;
                                localVar4 = dataList.get(0);
                                localVar5 = status;
                                localVar6 = param2;
                                localVar7 = localVar4 + localVar5;
                                localVar8 = localVar6 + localVar7;
                                dataList.add(localVar8);
                                method1Counter++;
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
                                method1Result = localVar8;
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

    public void complexMethod2(List<String> items, int threshold) {
        int total = 0;
        int count = 0;
        String result = "";
        boolean found = false;
        int index = 0;
        String temp1 = "";
        String temp2 = "";
        String temp3 = "";

        if (items != null && items.size() > 0) {
            for (String item : items) {
                if (item != null) {
                    if (item.length() > threshold) {
                        if (dataMap.containsKey(item)) {
                            if (dataMap.get(item) > 0) {
                                total += dataMap.get(item);
                                count++;
                                temp1 = item;
                                temp2 = String.valueOf(dataMap.get(item));
                                temp3 = temp1 + ":" + temp2;
                                result += temp3 + ";";
                                found = true;
                            } else {
                                temp1 = item;
                                temp2 = "0";
                                temp3 = temp1 + ":" + temp2;
                            }
                        } else {
                            dataMap.put(item, 1);
                            temp1 = item;
                            temp2 = "1";
                            temp3 = temp1 + ":" + temp2;
                            index++;
                        }
                    } else {
                        temp1 = item;
                        temp2 = "short";
                        temp3 = temp1 + ":" + temp2;
                    }
                } else {
                    temp1 = "null";
                    temp2 = "null";
                    temp3 = "null:null";
                }
            }
        } else {
            total = 0;
            count = 0;
            result = "";
            found = false;
            index = 0;
        }

        if (found) {
            method2Result = result;
            method2Total = total;
        }
    }

    public void complexMethod3(String input, int mode) {
        String var1 = "";
        String var2 = "";
        String var3 = "";
        String var4 = "";
        int num1 = 0;
        int num2 = 0;
        int num3 = 0;
        boolean check1 = false;
        boolean check2 = false;

        if (mode == 1) {
            if (input != null && input.length() > 0) {
                for (int i = 0; i < input.length(); i++) {
                    if (Character.isDigit(input.charAt(i))) {
                        if (num1 < 10) {
                            num1++;
                            var1 += input.charAt(i);
                            check1 = true;
                        } else {
                            num2++;
                            var2 += input.charAt(i);
                        }
                    } else if (Character.isLetter(input.charAt(i))) {
                        if (num2 < 10) {
                            num2++;
                            var3 += input.charAt(i);
                            check2 = true;
                        } else {
                            num3++;
                            var4 += input.charAt(i);
                        }
                    } else {
                        var1 += "?";
                        var2 += "?";
                    }
                }
            }
        } else if (mode == 2) {
            for (int j = 0; j < method3Value; j++) {
                if (j % 3 == 0) {
                    num1 += j;
                    var1 += String.valueOf(j);
                } else if (j % 3 == 1) {
                    num2 += j;
                    var2 += String.valueOf(j);
                } else {
                    num3 += j;
                    var3 += String.valueOf(j);
                }
            }
        }

        if (check1 && check2) {
            method3Result = var1 + var2 + var3 + var4;
            method3Value = num1 + num2 + num3;
        }
    }

    public void simpleMethod1() {
        dataList.add("simple");
    }

    public void simpleMethod2() {
        counter++;
    }

    public String getStatus() {
        return status;
    }

    public int getCounter() {
        return counter;
    }
}
