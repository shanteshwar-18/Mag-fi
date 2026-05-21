package com.magfi.navigator.core

import android.util.Log
import kotlin.math.sqrt
import java.util.PriorityQueue

/**
 * Graph data classes used by RoutingEngine and MapCanvasView.
 */
data class GraphNode(val id: String, val x: Float, val y: Float)
data class GraphEdge(val from: String, val to: String, val weight: Float)

/**
 * RoutingEngine — Dijkstra shortest path on a hardcoded building graph.
 *
 * Nodes represent named locations (rooms, corridors, stairs) with (x,y) meter
 * coordinates in the same space as PdrTracker. Edges are bidirectional and
 * auto-weighted by Euclidean distance.
 *
 * findPath() returns the full ordered list of GraphNodes from start to end,
 * or null if the destination is unreachable.
 */
class RoutingEngine {

    companion object {
        private const val TAG = "RoutingEngine"
    }

    private val nodes     = mutableMapOf<String, GraphNode>()
    private val adjacency = mutableMapOf<String, MutableList<Pair<String, Float>>>()

    fun addNode(id: String, x: Float, y: Float) {
        nodes[id] = GraphNode(id, x, y)
        adjacency.getOrPut(id) { mutableListOf() }
    }

    /**
     * Add an undirected edge between two nodes.
     * Weight is automatically set to the Euclidean distance between them.
     */
    fun addEdge(fromId: String, toId: String) {
        val from = nodes[fromId] ?: return
        val to   = nodes[toId]   ?: return
        val dist = sqrt((to.x - from.x) * (to.x - from.x) + (to.y - from.y) * (to.y - from.y))
        adjacency.getOrPut(fromId) { mutableListOf() }.add(Pair(toId, dist))
        adjacency.getOrPut(toId)   { mutableListOf() }.add(Pair(fromId, dist))
    }

    /**
     * Dijkstra shortest path from startId to endId.
     * Returns ordered List<GraphNode> from start to end, or null if unreachable.
     */
    fun findPath(startId: String, endId: String): List<GraphNode>? {
        if (!nodes.containsKey(startId) || !nodes.containsKey(endId)) {
            Log.w(TAG, "findPath: unknown node(s) — start=$startId end=$endId")
            return null
        }
        if (startId == endId) return listOf(nodes[startId]!!)

        val dist = mutableMapOf<String, Float>().withDefault { Float.MAX_VALUE }
        val prev = mutableMapOf<String, String?>()
        dist[startId] = 0f

        // PriorityQueue sorted by ascending cost: Pair(cost, nodeId)
        val pq = PriorityQueue<Pair<Float, String>>(compareBy { it.first })
        pq.add(Pair(0f, startId))

        while (pq.isNotEmpty()) {
            val (cost, nodeId) = pq.poll()

            // Skip stale entries (prevents infinite loop on revisited nodes)
            if (cost > (dist[nodeId] ?: Float.MAX_VALUE)) continue

            if (nodeId == endId) break

            for ((neighbor, weight) in adjacency[nodeId] ?: emptyList()) {
                val newCost = cost + weight
                if (newCost < (dist[neighbor] ?: Float.MAX_VALUE)) {
                    dist[neighbor] = newCost
                    prev[neighbor] = nodeId
                    pq.add(Pair(newCost, neighbor))
                }
            }
        }

        // Reconstruct path from prev[] map (backward from end to start)
        if (!prev.containsKey(endId) && endId != startId) {
            Log.w(TAG, "findPath: $endId is unreachable from $startId")
            return null
        }

        val path = mutableListOf<GraphNode>()
        var cur: String? = endId
        while (cur != null) {
            nodes[cur]?.let { path.add(it) }
            cur = prev[cur]
        }
        path.reverse()   // forward order: start → end
        Log.d(TAG, "Route: ${path.joinToString(" → ") { it.id }}")
        return path
    }

    fun getNode(id: String): GraphNode? = nodes[id]
    fun getAllNodeIds(): List<String> = nodes.keys.toList()
}
