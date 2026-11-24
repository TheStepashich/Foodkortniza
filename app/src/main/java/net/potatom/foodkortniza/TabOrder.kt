package net.potatom.foodkortniza

object TabOrder {
    /** Чистая логика перестановки — вынесена из UI, чтобы её можно было протестировать. */
    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (from !in ids.indices || to !in ids.indices || from == to) return ids
        val result = ids.toMutableList()
        val item = result.removeAt(from)
        result.add(to, item)
        return result
    }

    fun containsX(rawX: Float, left: Int, width: Int): Boolean =
        rawX >= left && rawX <= left + width
}
