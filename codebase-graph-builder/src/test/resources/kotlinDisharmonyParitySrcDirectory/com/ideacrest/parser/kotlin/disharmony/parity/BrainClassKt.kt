package com.ideacrest.parser.kotlin.disharmony.parity

import java.util.ArrayList
import java.util.HashMap

/**
 * Kotlin disharmony parity fixture — Kotlin twin of the Java `BrainClassExample`.
 *
 * Contains two `complexMethodX` methods that each satisfy the
 * Brain Method criteria (LOC > 65, CYCLO >= 4, MAXNESTING >= 5,
 * NOAV > 7), plus additional complex helpers to push LOC past
 * VERY_HIGH (195) and WMC past VERY_HIGH (47). Drives the Brain
 * Class and Brain Method detectors (Lanza & Marinescu Fig. 5.12).
 *
 * Plain-text fixture for OpenRewrite's Kotlin parser, NOT compiled
 * by the Maven build.
 */
class BrainClassKt {

    private val dataList: MutableList<String> = ArrayList()
    private val dataMap: MutableMap<String, Int> = HashMap()
    private var counter: Int = 0
    private var status: String = ""
    private var flag: Boolean = false

    private var method1Result: String = ""
    private var method1Counter: Int = 0

    private var method2Result: String = ""
    private var method2Total: Int = 0

    private var method3Result: String = ""
    private var method3Value: Int = 0

    private var m4result: String = ""
    private var m4low: Int = 0
    private var m4high: Int = 0

    private var m5flag: Boolean = false
    private var m5data: String = ""

    fun complexMethod1(param1: Int, param2: String?, param3: Boolean) {
        var localVar1 = 0
        var localVar2 = 0
        var localVar3 = 0
        var localVar4 = ""
        var localVar5 = ""
        var localVar6 = ""
        var localVar7 = ""
        var localVar8 = ""

        if (param1 > 0) {
            if (param2 != null) {
                if (param3) {
                    for (i in 0 until param1) {
                        if (i % 2 == 0) {
                            if (dataList.isNotEmpty()) {
                                localVar1 = dataList.size
                                localVar2 = counter
                                localVar3 = localVar1 + localVar2
                                localVar4 = dataList[0]
                                localVar5 = status
                                localVar6 = param2
                                localVar7 = localVar4 + localVar5
                                localVar8 = localVar6 + localVar7
                                dataList.add(localVar8)
                                method1Counter++
                            } else {
                                localVar1 = 0
                                localVar2 = 0
                                localVar3 = 0
                                localVar4 = ""
                                localVar5 = ""
                                localVar6 = ""
                                localVar7 = ""
                                localVar8 = ""
                            }
                        } else {
                            if (flag) {
                                localVar1 = counter
                                localVar2 = param1
                                localVar3 = localVar1 * localVar2
                                localVar4 = localVar3.toString()
                                localVar5 = status
                                localVar6 = param2
                                localVar7 = localVar4 + localVar5
                                localVar8 = localVar6 + localVar7
                                method1Result = localVar8
                            }
                        }
                    }
                } else {
                    localVar1 = counter
                    localVar2 = param1
                    localVar3 = localVar1 + localVar2
                    localVar4 = localVar3.toString()
                    localVar5 = status
                    localVar6 = param2
                    localVar7 = localVar4 + localVar5
                    localVar8 = localVar6 + localVar7
                }
            } else {
                localVar1 = 0
                localVar2 = 0
                localVar3 = 0
                localVar4 = ""
                localVar5 = ""
                localVar6 = ""
                localVar7 = ""
                localVar8 = ""
            }
        } else {
            localVar1 = counter
            localVar2 = param1
            localVar3 = localVar1 - localVar2
            localVar4 = localVar3.toString()
            localVar5 = status
            localVar6 = param2 ?: ""
            localVar7 = localVar4 + localVar5
            localVar8 = localVar6 + localVar7
        }
    }

    fun complexMethod2(items: List<String>?, threshold: Int) {
        var total = 0
        var count = 0
        var result = ""
        var found = false
        var index = 0
        var temp1 = ""
        var temp2 = ""
        var temp3 = ""
        var prefix = ""
        var suffix = ""
        var maxVal = 0

        if (items != null && items.isNotEmpty()) {
            for (item in items) {
                if (item != null) {
                    if (item.length > threshold) {
                        if (dataMap.containsKey(item)) {
                            if (dataMap[item]!! > 0) {
                                total += dataMap[item]!!
                                count++
                                temp1 = item
                                temp2 = dataMap[item].toString()
                                temp3 = "$temp1:$temp2"
                                result += "$temp3;"
                                found = true
                            } else {
                                temp1 = item
                                temp2 = "0"
                                temp3 = "$temp1:$temp2"
                            }
                        } else {
                            dataMap[item] = 1
                            temp1 = item
                            temp2 = "1"
                            temp3 = "$temp1:$temp2"
                            index++
                        }
                    } else {
                        temp1 = item
                        temp2 = "short"
                        temp3 = "$temp1:$temp2"
                    }
                } else {
                    temp1 = "null"
                    temp2 = "null"
                    temp3 = "null:null"
                }
            }
        } else {
            total = 0
            count = 0
            result = ""
            found = false
            index = 0
        }

        if (count > 0) {
            prefix = "count:$count"
            suffix = "total:$total"
            maxVal = total / count
            method2Result = "$prefix;$result;$suffix"
            method2Total = maxVal
        } else if (index > 0) {
            prefix = "new:$index"
            suffix = "none"
            method2Result = "$prefix;$suffix"
            method2Total = index
        } else {
            method2Result = if (found) result else ""
            method2Total = if (found) total else 0
            maxVal = 0
        }
    }

    fun complexMethod3(input: String?, mode: Int) {
        var var1 = ""
        var var2 = ""
        var var3 = ""
        var var4 = ""
        var num1 = 0
        var num2 = 0
        var num3 = 0
        var check1 = false
        var check2 = false

        if (mode == 1) {
            if (input != null && input.isNotEmpty()) {
                for (i in 0 until input.length) {
                    val c = input[i]
                    if (c.isDigit()) {
                        if (num1 < 10) {
                            num1++
                            var1 += c
                            check1 = true
                        } else {
                            num2++
                            var2 += c
                        }
                    } else if (c.isLetter()) {
                        if (num2 < 10) {
                            num2++
                            var3 += c
                            check2 = true
                        } else {
                            num3++
                            var4 += c
                        }
                    } else {
                        var1 += "?"
                        var2 += "?"
                    }
                }
            }
        } else if (mode == 2) {
            for (j in 0 until method3Value) {
                if (j % 3 == 0) {
                    num1 += j
                    var1 += j.toString()
                } else if (j % 3 == 1) {
                    num2 += j
                    var2 += j.toString()
                } else {
                    num3 += j
                    var3 += j.toString()
                }
            }
        }

        if (check1 && check2) {
            method3Result = var1 + var2 + var3 + var4
            method3Value = num1 + num2 + num3
        }
    }

    // CC=7 (4 if-elif branches + for + if inside for)
    fun complexMethod4(value: Int, prefix: String): String {
        var r1 = ""
        var r2 = ""
        var r3 = ""
        var n1 = 0
        if (value > 100) {
            r1 = "$prefix:vhigh"
            m4high = value
        } else if (value > 50) {
            r1 = "$prefix:high"
            n1 = value / 2
            m4high = n1
        } else if (value > 20) {
            r1 = "$prefix:mid"
            n1 = value
        } else if (value > 0) {
            r1 = "$prefix:low"
            m4low = value
            n1 = value
        } else {
            r1 = "$prefix:zero"
            m4low = 0
        }
        for (k in 0 until n1) {
            r2 += "$k;"
            if (r2.length > 50) {
                r3 = r2.substring(0, 50)
                break
            }
        }
        m4result = r1 + r2 + r3
        return m4result
    }

    // CC=6 (if + 2 elif + inner if)
    fun complexMethod5(key: String?, strict: Boolean): Int {
        var n1 = 0
        var s1 = ""
        if (key == null) {
            return 0
        } else if (strict) {
            val len = key.length
            n1 = len * 2
            s1 = if (len > 5) key.substring(0, 5) else key
            m5flag = true
        } else if (key.length > 5) {
            n1 = key.length
            s1 = key
            m5flag = false
        } else {
            n1 = 1
            s1 = key
        }
        if (m5flag) {
            m5data = "$s1:$n1"
        }
        return n1
    }

    // CC=8 (for + if + 4 elif + nested if) — pushes WMC past VERY_HIGH(47)
    fun complexMethod6(values: List<Int>?, threshold: Int): Int {
        var sum = 0
        var tally = 0
        if (values == null || values.isEmpty()) {
            return 0
        }
        for (v in values) {
            if (v > threshold) {
                if (v > 100) {
                    sum += v
                    tally++
                } else if (v > 50) {
                    sum += v / 2
                    tally++
                } else if (v > 25) {
                    sum += v
                } else if (v > 10) {
                    sum -= v
                } else {
                    tally++
                }
            }
        }
        return sum + tally
    }

    fun simpleMethod1() {
        dataList.add("simple")
    }

    fun simpleMethod2() {
        counter++
    }

    fun getStatus(): String = status

    fun getCounter(): Int = counter
}
