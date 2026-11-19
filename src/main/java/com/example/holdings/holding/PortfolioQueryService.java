package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.holdings.pnl.PnlCalculator;
import com.example.holdings.pnl.PositionCalculator;
import com.example.holdings.pnl.TaxRules;
import com.example.holdings.pnl.Summary;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.StockQuote;

/**
 * 讀取：組出部位、交易明細與總計。只讀資料庫（交易＋行情快取），不查官方網站。
 */
@Service
public class PortfolioQueryService {

    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final QuoteService quoteService;
    private final TaxRules taxRules;
    private final Clock clock;

    public PortfolioQueryService(HoldingRepository holdings, TradeRepository trades, QuoteService quoteService,
                                 TaxRules taxRules, Clock clock) {
        this.holdings = holdings;
        this.trades = trades;
        this.quoteService = quoteService;
        this.taxRules = taxRules;
        this.clock = clock;
    }

    /** 所有部位（依 id）。includeClosed=false 只回還有股數的。沒有任何交易的空部位不列出。 */
    @Transactional(readOnly = true)
    public List<PositionView> positions(boolean includeClosed) {
        List<Holding> all = holdings.findAllByOrderByIdAsc();
        Map<Long, List<Trade>> byHolding = trades.findByHoldingIdIn(all.stream().map(Holding::getId).toList())
                .stream().collect(Collectors.groupingBy(Trade::getHoldingId));
        Map<String, StockQuote> quotes = quoteService.findAll(all.stream().map(Holding::getCode).toList())
                .stream().collect(Collectors.toMap(StockQuote::getCode, Function.identity()));
        List<PositionView> result = new ArrayList<>();
        for (Holding h : all) {
            List<Trade> list = byHolding.getOrDefault(h.getId(), List.of());
            if (list.isEmpty()) {
                continue;
            }
            PositionView v = toView(h, list, quotes.get(h.getCode()));
            if (includeClosed || v.isOpen()) {
                result.add(v);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PositionView position(long id) {
        Holding h = holdings.findById(id).orElseThrow(() -> HoldingException.notFound(id));
        List<Trade> list = trades.findByHoldingId(id);
        if (list.isEmpty()) {
            throw HoldingException.notFound(id);
        }
        return toView(h, list, quoteService.find(h.getCode()).orElse(null));
    }

    @Transactional(readOnly = true)
    public TradeView trade(long tradeId) {
        Trade t = trades.findById(tradeId).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
        return position(t.getHoldingId()).trades().stream()
                .filter(x -> x.id() == tradeId).findFirst().orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
    }

    public PortfolioSummary summary() {
        return summarize(positions(true));
    }

    public static PortfolioSummary summarize(List<PositionView> views) {
        long totalCost = 0;
        long marketValue = 0;
        long unrealized = 0;
        long realized = 0;
        int missing = 0;
        int open = 0;
        for (PositionView v : views) {
            realized += v.realizedPnl();
            if (!v.isOpen()) {
                continue;
            }
            open++;
            totalCost += v.cost();
            if (v.unrealizedPnl() == null) {
                missing++;
            } else {
                marketValue += v.marketValue();
                unrealized += v.unrealizedPnl();
            }
        }
        return new PortfolioSummary(totalCost, marketValue, unrealized,
                PnlCalculator.returnPct(BigDecimal.valueOf(unrealized), BigDecimal.valueOf(totalCost)),
                realized, missing, open);
    }

    /** App 1.0 的總計：只看還有股數的部位，pnl 是未實現損益。 */
    public Summary v1Summary() {
        PortfolioSummary s = summary();
        return new Summary(s.totalCost(), s.marketValue(), s.unrealizedPnl(), s.unrealizedReturnPct(), s.quoteMissing());
    }

    private PositionView toView(Holding h, List<Trade> list, StockQuote q) {
        PositionCalculator.Position p = PositionCalculator.compute(h.getCode(),
                list.stream().map(Trade::toInput).toList(), taxRules);
        Map<Long, Trade> byId = list.stream().collect(Collectors.toMap(Trade::getId, Function.identity()));
        List<TradeView> tradeViews = new ArrayList<>();
        for (PositionCalculator.TradeResult r : p.trades()) {
            Trade t = byId.get(r.trade().id());
            tradeViews.add(new TradeView(t.getId(), h.getId(), t.getSide(), t.getTradeDate(), t.isDateEstimated(),
                    t.getShares(), t.getPrice(), PnlCalculator.toYuan(r.amount()), PnlCalculator.toYuan(r.fee()),
                    PnlCalculator.toYuan(r.tax()),
                    r.costBasis() == null ? null : PnlCalculator.toYuan(r.costBasis()),
                    r.realizedPnl() == null ? null : PnlCalculator.toYuan(r.realizedPnl()),
                    r.sharesAfter(), t.getVersion()));
        }
        tradeViews.sort(Comparator.comparing(TradeView::date).reversed()
                .thenComparing(Comparator.comparingLong(TradeView::id).reversed()));

        BigDecimal close = q == null ? null : q.getClosePrice();
        PnlCalculator.Result u = null;
        if (close != null && p.shares() > 0) {
            u = PnlCalculator.unrealized(h.getCode(), p.shares(), p.cost(), close, LocalDate.now(clock), taxRules);
        }
        return new PositionView(
                h.getId(),
                h.getCode(),
                q == null ? h.getCode() : q.getName(),
                q == null ? null : q.getMarket(),
                p.shares(),
                p.avgCost(),
                p.avgPrice(),
                p.costYuan(),
                close,
                close == null ? null : q.getPriceDate(),
                u == null ? null : u.marketValue(),
                u == null ? null : u.pnl(),
                u == null ? null : u.returnPct(),
                p.realizedYuan(),
                h.getVersion(),
                tradeViews);
    }
}
