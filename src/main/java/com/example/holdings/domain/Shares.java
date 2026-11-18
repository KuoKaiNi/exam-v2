package com.example.holdings.domain;

/** 股數顯示：1500 股顯示「1 張 500 股」，300 股顯示「300 股」。 */
public final class Shares {

    private Shares() {
    }

    public static String format(int shares) {
        int lots = shares / 1000;
        int rest = shares % 1000;
        if (lots == 0) {
            return rest + " 股";
        }
        if (rest == 0) {
            return lots + " 張";
        }
        return lots + " 張 " + rest + " 股";
    }
}
