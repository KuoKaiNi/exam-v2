package com.example.holdings.web;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/** 畫面用的數字格式。台股慣例：紅色是賺（up）、綠色是虧（down）。 */
public final class Fmt {

    public static final Fmt INSTANCE = new Fmt();

    private Fmt() {
    }

    /** +93,708 / -10,132 / 0 */
    public String signedMoney(Long v) {
        if (v == null) {
            return "未取得";
        }
        String s = new DecimalFormat("#,##0").format(Math.abs(v));
        return v > 0 ? "+" + s : v < 0 ? "-" + s : s;
    }

    public String money(long v) {
        return new DecimalFormat("#,##0").format(v);
    }

    /** +9.36% / -3.37% */
    public String signedPct(BigDecimal v) {
        if (v == null) {
            return "";
        }
        String s = new DecimalFormat("0.00").format(v.abs()) + "%";
        return v.signum() > 0 ? "+" + s : v.signum() < 0 ? "-" + s : s;
    }

    public String price(BigDecimal v) {
        return v == null ? "未取得" : new DecimalFormat("#,##0.00").format(v);
    }

    public String cls(Long v) {
        if (v == null) {
            return "missing";
        }
        return v > 0 ? "up" : v < 0 ? "down" : "flat";
    }
}
