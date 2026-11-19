package com.transporterassignment.solver;

/** Binomial coefficients that saturate at {@link Long#MAX_VALUE} instead of overflowing. */
public final class Combinatorics {

    private Combinatorics() {
    }

    /** C(n, k), or {@link Long#MAX_VALUE} if the true value does not fit in a long; 0 when k is out of range. */
    public static long combinations(int n, int k) {
        if (k < 0 || n < 0 || k > n) {
            return 0;
        }
        int r = Math.min(k, n - k);
        long result = 1;
        for (int i = 1; i <= r; i++) {
            // result = C(n - r + i - 1, i - 1); next = result * (n - r + i) / i, kept exact by dividing out the gcd first.
            long factor = (long) n - r + i;
            long g = gcd(result, i);
            long reduced = result / g;
            long divisor = i / g;
            factor /= divisor; // exact: divisor is coprime with reduced and divides reduced * factor
            if (reduced > Long.MAX_VALUE / factor) {
                return Long.MAX_VALUE;
            }
            result = reduced * factor;
        }
        return result;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
