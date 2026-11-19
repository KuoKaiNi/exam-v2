package com.example.holdings.holding;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.holdings.pnl.PnlCalculator;
import com.example.holdings.pnl.Summary;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.StockQuote;

/**
 * 組出清單要顯示的資料。只讀資料庫（持股＋行情快取），不會去查官方網站，
 * 所以官方連不上時清單照樣顯示，收盤價那格是「未取得」。
 */
@Service
public class HoldingQueryService {

    private final HoldingService holdingService;
    private final QuoteService quoteService;

    public HoldingQueryService(HoldingService holdingService, QuoteService quoteService) {
        this.holdingService = holdingService;
        this.quoteService = quoteService;
    }

    public List<HoldingView> list() {
        List<Holding> holdings = holdingService.list();
        Map<String, StockQuote> quotes = quoteService.findAll(holdings.stream().map(Holding::getCode).toList())
                .stream().collect(Collectors.toMap(StockQuote::getCode, Function.identity()));
        return holdings.stream().map(h -> toView(h, quotes.get(h.getCode()))).toList();
    }

    /** 總計：取不到收盤價的不算進市值與盈虧，但算進總成本。 */
    public Summary summary() {
        return summarize(list());
    }

    public static Summary summarize(List<HoldingView> views) {
        long totalCost = 0;
        long marketValue = 0;
        long pnl = 0;
        int missing = 0;
        for (HoldingView v : views) {
            totalCost += v.cost();
            if (v.pnl() == null) {
                missing++;
            } else {
                marketValue += v.marketValue();
                pnl += v.pnl();
            }
        }
        return new Summary(totalCost, marketValue, pnl,
                PnlCalculator.returnPct(BigDecimal.valueOf(pnl), BigDecimal.valueOf(totalCost)), missing);
    }

    public HoldingView get(long id) {
        Holding h = holdingService.get(id);
        return toView(h, quoteService.find(h.getCode()).orElse(null));
    }

    public HoldingView toView(Holding h) {
        return toView(h, quoteService.find(h.getCode()).orElse(null));
    }

    private static HoldingView toView(Holding h, StockQuote q) {
        BigDecimal close = q == null ? null : q.getClosePrice();
        PnlCalculator.Result r = close == null ? null
                : PnlCalculator.calculate(h.getCode(), h.getLots(), h.getBuyPrice(), close);
        return new HoldingView(
                h.getId(),
                h.getCode(),
                q == null ? h.getCode() : q.getName(),
                q == null ? null : q.getMarket(),
                h.getLots(),
                h.getBuyPrice(),
                close,
                close == null ? null : q.getPriceDate(),
                PnlCalculator.cost(h.getLots(), h.getBuyPrice()),
                r == null ? null : r.marketValue(),
                r == null ? null : r.pnl(),
                r == null ? null : r.returnPct());
    }
}
