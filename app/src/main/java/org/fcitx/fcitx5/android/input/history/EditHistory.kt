package org.fcitx.fcitx5.android.input.history

/** A session-only branching history; editing an old node keeps its other children. */
class EditHistory {
    data class Node(val id: Int, var parent: Int?, val text: String, val start: Int, val end: Int)
    val nodes = mutableListOf<Node>()
    var current: Int? = null
        private set
    private var nextId = 1
    fun clear() { nodes.clear(); current = null; nextId = 1 }
    fun select(id: Int) { require(nodes.any { it.id == id }); current = id }
    fun record(text: String, start: Int, end: Int) {
        val active = nodes.find { it.id == current }
        if (active?.text == text) return
        val parent = nodes.find { it.id == active?.parent && it.text == text }
        if (parent != null) { current = parent.id; return }
        val child = nodes.lastOrNull { it.parent == current && it.text == text }
        if (child != null) { current = child.id; return }
        nodes.add(Node(nextId++, current, text, start, end))
        current = nodes.last().id
        if (nodes.size > 100) {
            val removed = nodes.removeAt(0)
            nodes.filter { it.parent == removed.id }.forEach { it.parent = null }
        }
    }
}
