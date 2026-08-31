package com.ideacrest.parser.kotlin.disharmony.parity

/**
 * Kotlin disharmony parity fixture — Kotlin twin of `SignificantDuplicationCrossClassB`.
 * 14-line clone of `SignificantDuplicationCrossClassKtA.computeResult`
 * (one line differs: `u - x` vs `u + x`) — long enough to satisfy the
 * Significant Duplication chain criterion.
 */
class SignificantDuplicationCrossClassKtB {

    fun computeResult(x: Int): Int {
        val p = x + 2
        val q = p * 3
        val r = q - 4
        val s = r / 5
        val t = s + 6
        val u = t * 7
        val v = u - x
        val w = v + 2
        val aa = w * 3
        val bb = aa - 4
        val cc = bb / 5
        val dd = cc + 6
        val ee = dd * 7
        return ee
    }
}
