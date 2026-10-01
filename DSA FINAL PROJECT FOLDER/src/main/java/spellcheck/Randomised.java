package spellcheck;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * MODULE 6 - RANDOMISED AND PARALLEL ALGORITHMS (small helper methods).
 *
 * LAS VEGAS vs MONTE CARLO
 *   Las Vegas  : always correct, running time is random   (e.g. randomised quicksort)
 *   Monte Carlo: fixed running time, answer is correct with some probability
 *                (e.g. Miller-Rabin, and our random accuracy test)
 *
 * Used in this project:
 *   reservoirSample  - pick k random items from a list/stream of unknown length, uniformly
 *   corrupt          - make a random typo (delete / insert / substitute / swap)
 *   millerRabin      - Monte Carlo primality test (standalone demo)
 *   blellochScan     - parallel prefix-sum (work-efficient scan), used by the parallel demo
 */
public class Randomised {

    /**
     * RESERVOIR SAMPLING: keep the first k items; the i-th item (i > k) replaces a random
     * kept item with probability k/i. PROOF SKETCH: item i is kept at step i with prob k/i and
     * survives each later step j with prob (1 - 1/j), so the product telescopes to k/n for
     * every item -> every item is in the sample with the SAME probability k/n (uniform).
     * Time O(n), space O(k), one pass.
     */
    public static List<String> reservoirSample(List<String> stream, int k, Random random) {
        List<String> reservoir = new ArrayList<>();
        int seen = 0;
        for (String item : stream) {
            seen++;
            if (reservoir.size() < k) {
                reservoir.add(item);
            } else {
                int j = random.nextInt(seen);        // 0 .. seen-1
                if (j < k) {
                    reservoir.set(j, item);
                }
            }
        }
        return reservoir;
    }

    /** Makes ONE random typo in a word; the result is always different from the input. */
    public static String corrupt(String word, Random random) {
        while (true) {
            StringBuilder sb = new StringBuilder(word);
            int operation = random.nextInt(4);
            int pos = random.nextInt(word.length());
            char letter = (char) ('a' + random.nextInt(26));
            if (operation == 0 && word.length() > 1) {
                sb.deleteCharAt(pos);                                   // missing letter
            } else if (operation == 1) {
                sb.insert(random.nextInt(word.length() + 1), letter);   // extra letter
            } else if (operation == 2) {
                sb.setCharAt(pos, letter);                              // wrong letter
            } else if (operation == 3 && word.length() > 1) {
                int p = Math.min(pos, word.length() - 2);               // swapped neighbours
                char a = sb.charAt(p);
                sb.setCharAt(p, sb.charAt(p + 1));
                sb.setCharAt(p + 1, a);
            }
            if (!sb.toString().equals(word)) {
                return sb.toString();
            }
        }
    }

    /**
     * MILLER-RABIN (Monte Carlo). Write n-1 = d * 2^r (d odd). For a random base a, n is
     * "probably prime" if a^d = 1 or a^(d*2^i) = -1 (mod n) for some i < r. Otherwise a is a
     * WITNESS and n is definitely composite. A composite passes one round with probability
     * <= 1/4, so k rounds give error <= 4^(-k). It also catches Carmichael numbers (like 561)
     * that fool the simple Fermat test.
     */
    public static boolean millerRabin(long n, int rounds, Random random) {
        if (n < 2) return false;
        if (n < 4) return true;
        if (n % 2 == 0) return false;
        long d = n - 1;
        int r = 0;
        while (d % 2 == 0) {
            d /= 2;
            r++;
        }
        BigInteger bigN = BigInteger.valueOf(n);
        BigInteger nMinusOne = bigN.subtract(BigInteger.ONE);
        for (int round = 0; round < rounds; round++) {
            long a = 2 + (long) (random.nextDouble() * (n - 3));      // random base in [2, n-2]
            BigInteger x = BigInteger.valueOf(a).modPow(BigInteger.valueOf(d), bigN);
            if (x.equals(BigInteger.ONE) || x.equals(nMinusOne)) continue;
            boolean composite = true;
            for (int i = 1; i < r; i++) {
                x = x.multiply(x).mod(bigN);
                if (x.equals(nMinusOne)) {
                    composite = false;
                    break;
                }
            }
            if (composite) return false;                              // found a witness
        }
        return true;
    }

    /**
     * BLELLOCH EXCLUSIVE PREFIX-SUM (scan). Two phases over a balanced binary tree:
     *   up-sweep   : build partial sums,
     *   down-sweep : push prefixes back down.
     * Inside each level, all iterations of the inner loop are INDEPENDENT, so on p processors
     * they could run in parallel: work = O(n), span = O(log n). (Here it is written
     * sequentially so it is easy to read; the loops show where the parallelism is.)
     */
    public static int[] blellochScan(int[] input) {
        int size = 1;
        while (size < input.length) size <<= 1;
        int[] t = Arrays.copyOf(input, size);
        for (int d = 1; d < size; d *= 2) {                       // up-sweep
            for (int i = 0; i < size; i += 2 * d) {
                t[i + 2 * d - 1] += t[i + d - 1];
            }
        }
        t[size - 1] = 0;
        for (int d = size / 2; d >= 1; d /= 2) {                  // down-sweep
            for (int i = 0; i < size; i += 2 * d) {
                int left = t[i + d - 1];
                t[i + d - 1] = t[i + 2 * d - 1];
                t[i + 2 * d - 1] += left;
            }
        }
        return Arrays.copyOf(t, input.length);
    }
}
