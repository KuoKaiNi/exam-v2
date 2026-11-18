package com.example.holdings.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 盈虧計算。純計算，不連證交所或櫃買中心。
 * 前三個例子是題目指定的；後面幾個是檢查手續費下限、ETF 稅率與無條件捨去。
 */
class PnlCalculatorTest {

    @Test
    void 一般股票_2330_一張_賺() {
        PnlCalculator.Result r = PnlCalculator.calculate("2330", 1, bd("1000.00"), bd("1100.00"));

        // 買進 1,000,000 + 手續費 1,425；賣出 1,100,000 - 手續費 1,567 - 證交稅 3,300
        assertEquals(1001425L, r.buyCost());
        assertEquals(1100000L, r.marketValue());
        assertEquals(93708L, r.pnl());
        assertEquals(bd("9.36"), r.returnPct());
    }

    @Test
    void ETF_0050_二張_虧() {
        PnlCalculator.Result r = PnlCalculator.calculate("0050", 2, bd("150.00"), bd("145.50"));

        // 買進 300,000 + 手續費 427；賣出 291,000 - 手續費 414 - 證交稅 291（ETF 0.1%）
        assertEquals(300427L, r.buyCost());
        assertEquals(291000L, r.marketValue());
        assertEquals(-10132L, r.pnl());
        assertEquals(bd("-3.37"), r.returnPct());
    }

    @Test
    void 手續費低於20元時以20元計() {
        PnlCalculator.Result r = PnlCalculator.calculate("2363", 1, bd("10.00"), bd("10.50"));

        // 買進 10,000 的手續費 14 → 20；賣出 10,500 的手續費 14 → 20；證交稅 31
        assertEquals(10020L, r.buyCost());
        assertEquals(429L, r.pnl());
        assertEquals(bd("4.28"), r.returnPct());
    }

    @Test
    void 手續費與證交稅都無條件捨去到元() {
        // 買進 100,000：手續費 142.5 → 142；賣出 105,000：手續費 149.625 → 149，稅 315
        PnlCalculator.Result r = PnlCalculator.calculate("2330", 1, bd("100.00"), bd("105.00"));

        assertEquals(100142L, r.buyCost());
        assertEquals(4394L, r.pnl());
        assertEquals(bd("4.39"), r.returnPct());
    }

    @Test
    void ETF證交稅是千分之一_一般股票是千分之三() {
        // 00878 一張，買 20.00 賣 22.00：賣出 22,000 的稅 ETF 為 22（一般股票會是 66）
        PnlCalculator.Result etf = PnlCalculator.calculate("00878", 1, bd("20.00"), bd("22.00"));
        PnlCalculator.Result stock = PnlCalculator.calculate("2330", 1, bd("20.00"), bd("22.00"));

        assertEquals(1919L, etf.pnl());
        assertEquals(bd("9.58"), etf.returnPct());
        assertEquals(etf.pnl() - 44L, stock.pnl());
    }

    @Test
    void 分母為零時收益率為零() {
        assertEquals(bd("0.00"), PnlCalculator.returnPct(0, 0));
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
