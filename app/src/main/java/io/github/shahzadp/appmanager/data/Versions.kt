package io.github.shahzadp.appmanager.data

/** A tiny version comparator for the tags the catalogue actually uses. */
object Versions {

    fun compare(left: String, right: String): Int {
        val a = segments(left)
        val b = segments(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String?): Boolean {
        if (installed.isNullOrBlank()) return true
        return compare(candidate, installed) > 0
    }

    private fun segments(raw: String): List<Int> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .takeWhile { it.isDigit() || it == '.' }
        return cleaned.split('.')
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }
}
