package spellcheck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MODULE 5 - NP-COMPLETENESS AND APPROXIMATION: Vertex Cover.
 *
 * VERTEX COVER: choose the fewest vertices so that EVERY edge has at least one chosen endpoint.
 *   - As a DECISION problem ("is there a cover of size <= k?") it is NP-complete
 *     (reduction chain: 3-SAT -> CLIQUE -> INDEPENDENT-SET -> VERTEX-COVER).
 *   - NP means: a proposed answer (the certificate) can be VERIFIED in polynomial time.
 *     isCover() below is exactly that verifier: O(E).
 *   - Finding the smallest cover is NP-hard, so we use an APPROXIMATION ALGORITHM.
 *
 * 2-APPROXIMATION via MAXIMAL MATCHING:
 *   Scan the edges; if neither endpoint is chosen yet, choose BOTH endpoints.
 *   Why the cover is at most 2 x optimal: the edges we picked form a matching (no shared
 *   vertex), and any cover must contain at least one endpoint of each matching edge, so
 *   OPT >= (matching size), while our cover has exactly 2 x (matching size) vertices.
 *
 * NOTE: this is a syllabus demonstration; it is not part of the spelling pipeline.
 */
public class Approx {

    /** Parses "0-1,1-2,2-3" into edges. */
    public static List<int[]> parseEdges(String text) {
        List<int[]> edges = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] ends = part.trim().split("-");
            if (ends.length == 2) {
                edges.add(new int[]{Integer.parseInt(ends[0].trim()), Integer.parseInt(ends[1].trim())});
            }
        }
        return edges;
    }

    /** Certificate verifier: does every edge have a chosen endpoint?  O(E). */
    public static boolean isCover(Set<Integer> chosen, List<int[]> edges) {
        for (int[] e : edges) {
            if (!chosen.contains(e[0]) && !chosen.contains(e[1])) {
                return false;
            }
        }
        return true;
    }

    /** 2-approximation. Returns the cover; matchingSize[0] receives the matching size. */
    public static Set<Integer> approximateCover(List<int[]> edges, int[] matchingSize) {
        Set<Integer> cover = new HashSet<>();
        int matched = 0;
        for (int[] e : edges) {
            if (!cover.contains(e[0]) && !cover.contains(e[1])) {
                cover.add(e[0]);
                cover.add(e[1]);
                matched++;
            }
        }
        matchingSize[0] = matched;
        return cover;
    }

    /** Exact minimum cover by trying every subset - exponential O(2^n * E), only for tiny graphs. */
    public static int exactMinimumCover(int vertexCount, List<int[]> edges) {
        int best = vertexCount;
        for (int mask = 0; mask < (1 << vertexCount); mask++) {
            int size = Integer.bitCount(mask);
            if (size >= best) continue;
            boolean ok = true;
            for (int[] e : edges) {
                if ((mask & (1 << e[0])) == 0 && (mask & (1 << e[1])) == 0) {
                    ok = false;
                    break;
                }
            }
            if (ok) best = size;
        }
        return best;
    }
}
