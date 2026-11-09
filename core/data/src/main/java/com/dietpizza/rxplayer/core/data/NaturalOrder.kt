package com.dietpizza.rxplayer.core.data

/**
 * Natural, case-insensitive string order: digit runs compare numerically, so "Ep 2" < "Ep 10".
 * Ties (e.g. "a" vs "A", "1" vs "01") fall back to fewer leading zeros, then plain [String.compareTo],
 * so the order is total and stable. Allocation-free.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        var zeroTie = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                // Skip leading zeros.
                val startA = i
                val startB = j
                while (i < a.length && a[i] == '0') i++
                while (j < b.length && b[j] == '0') j++
                val zerosA = i - startA
                val zerosB = j - startB
                val numStartA = i
                val numStartB = j
                while (i < a.length && a[i].isAsciiDigit()) i++
                while (j < b.length && b[j].isAsciiDigit()) j++
                val lenA = i - numStartA
                val lenB = j - numStartB
                if (lenA != lenB) return lenA - lenB
                for (k in 0 until lenA) {
                    val d = a[numStartA + k] - b[numStartB + k]
                    if (d != 0) return d
                }
                if (zeroTie == 0 && zerosA != zerosB) zeroTie = zerosA - zerosB
            } else {
                if (ca != cb) {
                    val la = ca.lowercaseChar()
                    val lb = cb.lowercaseChar()
                    if (la != lb) return la - lb
                }
                i++
                j++
            }
        }
        val remaining = (a.length - i) - (b.length - j)
        if (remaining != 0) return remaining
        if (zeroTie != 0) return zeroTie
        return a.compareTo(b)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
