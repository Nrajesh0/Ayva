package com.focusbyrj.app.util

/** Case-sensitive distance over UTF-16 characters; callers retain their matching thresholds. */
internal fun editDistance(lhs: CharSequence, rhs: CharSequence): Int {
    var cost = IntArray(lhs.length + 1) { it }
    var next = IntArray(lhs.length + 1)
    for (i in 1..rhs.length) {
        next[0] = i
        for (j in 1..lhs.length) {
            val substitution = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
            next[j] = minOf(cost[j] + 1, next[j - 1] + 1, cost[j - 1] + substitution)
        }
        val previous = cost
        cost = next
        next = previous
    }
    return cost[lhs.length]
}
