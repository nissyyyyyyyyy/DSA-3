package spellcheck;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * MODULE 4 - NETWORK FLOW: max-flow with Edmonds-Karp and Dinic, plus min-cut.
 *
 * MAX-FLOW PROBLEM: a directed graph, each edge has a CAPACITY, a SOURCE s and a SINK t.
 *   Find the largest flow from s to t such that
 *     - capacity: flow on an edge <= its capacity
 *     - conservation: for every node except s and t, flow in = flow out.
 *   With integer capacities, an integer max-flow always exists (so matchings come out whole).
 *
 * RESIDUAL GRAPH: for every edge u->v we also keep a reverse edge v->u whose capacity is the
 *   flow already sent. Pushing flow "back" along it undoes an earlier decision.
 *
 * FORD-FULKERSON METHOD: repeat { find an augmenting path s->t in the residual graph,
 *   push the bottleneck (smallest residual capacity on the path) } until no path exists.
 *   Why correct: when no path exists, the nodes reachable from s form a cut whose capacity
 *   equals the flow  ->  MAX-FLOW MIN-CUT THEOREM.
 *
 * EDMONDS-KARP = Ford-Fulkerson where the path is found by BFS (fewest edges).   O(V * E^2)
 * DINIC        = BFS builds "levels"; then a DFS sends a BLOCKING FLOW along level-increasing
 *                edges only; repeat.                                              O(V^2 * E)
 *
 * MIN-CUT: after max-flow, the nodes reachable from s in the residual graph = side S.
 *   The edges from S to the rest are the minimum cut; their total capacity = max-flow.
 */
public class MaxFlow {

    private static class Edge {
        final int to;
        int cap;                  // remaining (residual) capacity
        final int originalCap;    // capacity when the edge was added (0 for reverse edges)
        final int rev;            // index of the reverse edge inside graph[to]

        Edge(int to, int cap, int rev) {
            this.to = to;
            this.cap = cap;
            this.originalCap = cap;
            this.rev = rev;
        }
    }

    private final int n;
    private final List<List<Edge>> graph = new ArrayList<>();
    private int[] level;          // Dinic: BFS level of each node
    private int[] next;           // Dinic: next edge to try at each node
    private int augmentations = 0;   // number of augmenting paths (Edmonds-Karp)
    private int phases = 0;          // number of BFS phases (Dinic)

    public MaxFlow(int nodeCount) {
        this.n = nodeCount;
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
    }

    /** Adds edge u -> v with the given capacity (and its reverse edge with capacity 0). */
    public void addEdge(int u, int v, int capacity) {
        int indexInU = graph.get(u).size();
        int indexInV = graph.get(v).size();
        graph.get(u).add(new Edge(v, capacity, indexInV));
        graph.get(v).add(new Edge(u, 0, indexInU));
    }

    /** Puts all capacities back to their original values so another algorithm can run. */
    public void reset() {
        for (List<Edge> edges : graph) {
            for (Edge e : edges) {
                e.cap = e.originalCap;
            }
        }
        augmentations = 0;
        phases = 0;
    }

    // ---------------------------------------------------------------- Edmonds-Karp
    public int edmondsKarp(int s, int t) {
        int flow = 0;
        while (true) {
            // BFS in the residual graph, remembering how we reached each node.
            int[] parentNode = new int[n];
            Edge[] parentEdge = new Edge[n];
            Arrays.fill(parentNode, -1);
            parentNode[s] = s;
            Queue<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty() && parentNode[t] == -1) {
                int u = queue.poll();
                for (Edge e : graph.get(u)) {
                    if (e.cap > 0 && parentNode[e.to] == -1) {
                        parentNode[e.to] = u;
                        parentEdge[e.to] = e;
                        queue.add(e.to);
                    }
                }
            }
            if (parentNode[t] == -1) {
                break;                              // no augmenting path -> flow is maximum
            }
            // Bottleneck = smallest residual capacity along the path.
            int bottleneck = Integer.MAX_VALUE;
            for (int v = t; v != s; v = parentNode[v]) {
                bottleneck = Math.min(bottleneck, parentEdge[v].cap);
            }
            // Augment: reduce forward capacity, increase reverse capacity.
            for (int v = t; v != s; v = parentNode[v]) {
                Edge e = parentEdge[v];
                e.cap -= bottleneck;
                graph.get(e.to).get(e.rev).cap += bottleneck;
            }
            flow += bottleneck;
            augmentations++;
        }
        return flow;
    }

    // ---------------------------------------------------------------------- Dinic
    public int dinic(int s, int t) {
        int flow = 0;
        while (buildLevels(s, t)) {                 // one phase = one BFS + one blocking flow
            next = new int[n];
            int pushed;
            while ((pushed = sendFlow(s, t, Integer.MAX_VALUE)) > 0) {
                flow += pushed;
            }
            phases++;
        }
        return flow;
    }

    /** BFS from s; level[v] = distance from s using edges that still have capacity. */
    private boolean buildLevels(int s, int t) {
        level = new int[n];
        Arrays.fill(level, -1);
        level[s] = 0;
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(s);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Edge e : graph.get(u)) {
                if (e.cap > 0 && level[e.to] == -1) {
                    level[e.to] = level[u] + 1;
                    queue.add(e.to);
                }
            }
        }
        return level[t] != -1;
    }

    /** DFS that only moves from level k to level k+1 (the "level graph"). */
    private int sendFlow(int u, int t, int limit) {
        if (u == t) {
            return limit;
        }
        for (; next[u] < graph.get(u).size(); next[u]++) {
            Edge e = graph.get(u).get(next[u]);
            if (e.cap > 0 && level[e.to] == level[u] + 1) {
                int pushed = sendFlow(e.to, t, Math.min(limit, e.cap));
                if (pushed > 0) {
                    e.cap -= pushed;
                    graph.get(e.to).get(e.rev).cap += pushed;
                    return pushed;
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------- results
    /** Targets of edges leaving u that carry flow (original capacity > 0 and now full). */
    public List<Integer> usedTargets(int u) {
        List<Integer> targets = new ArrayList<>();
        for (Edge e : graph.get(u)) {
            if (e.originalCap > 0 && e.cap == 0) {
                targets.add(e.to);
            }
        }
        return targets;
    }

    /** Min-cut edges {from, to, capacity}; call AFTER a max-flow algorithm has finished. */
    public List<int[]> minCutEdges(int s) {
        boolean[] reachable = new boolean[n];
        Queue<Integer> queue = new ArrayDeque<>();
        reachable[s] = true;
        queue.add(s);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Edge e : graph.get(u)) {
                if (e.cap > 0 && !reachable[e.to]) {
                    reachable[e.to] = true;
                    queue.add(e.to);
                }
            }
        }
        List<int[]> cut = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            if (!reachable[u]) continue;
            for (Edge e : graph.get(u)) {
                if (e.originalCap > 0 && !reachable[e.to]) {
                    cut.add(new int[]{u, e.to, e.originalCap});
                }
            }
        }
        return cut;
    }

    public int getAugmentations() { return augmentations; }
    public int getPhases() { return phases; }
}
