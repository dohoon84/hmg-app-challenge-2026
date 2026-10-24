package com.krazylabs.miri.mvp

/**
 * While routing, up to three consecutive empty TBT lists keep the previous list.
 * A fourth empty still does not mean the route ended. Routing false clears it.
 */
class TbtGuard {
    private var kept: List<String> = emptyList()
    private var empties: Int = 0

    fun accept(names: List<String>, routing: Boolean): List<String> {
        if (names.isNotEmpty()) {
            empties = 0
            kept = names
            return kept
        }
        if (!routing) {
            clear()
            return kept
        }
        empties += 1
        return kept
    }

    fun clear() {
        kept = emptyList()
        empties = 0
    }
}

fun countRoad(names: List<String>): Pair<Int, Int> {
    var tolls = 0
    var highways = 0
    for (name in names) {
        if (name == "TURN_TOLL") tolls += 1
        if (name.startsWith("TURN_ENTER_IC")) highways += 1
    }
    return tolls to highways
}
