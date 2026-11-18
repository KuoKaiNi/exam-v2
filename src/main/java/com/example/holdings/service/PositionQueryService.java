package com.example.holdings.service;

import com.example.holdings.config.ClockConfig;
import com.example.holdings.domain.Holding;
import com.example.holdings.domain.HoldingRepository;
import com.example.holdings.domain.Ledger;
import com.example.holdings.domain.PnlCalculator;
import com.example.holdings.domain.Trade;
import com.example.holdings.domain.TradeRepository;
import com.example.holdings.domain.TaxProperties;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteService.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionQueryService {

    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final QuoteService quotes;
    private final TaxProperties taxProperties;
    private final Clock clock;

    public PositionQueryService(HoldingRepository holdings, TradeRepository trades, QuoteService quotes,
                                TaxProperties taxProperties, Clock clock) {
        this.holdings = holdings;
        this.trades = trades;
        this.quotes = quotes;
        this.taxProperties = taxProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PositionView> list() {
        return holdings.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PositionView get(Long id) {
        return view(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Holding findOrThrow(Long id) {
        return holdings.findById(id).orElseThrow(PositionService::notFound);
    }

    PositionView view(Holding h) {
        List<Trade> ts = trades.findByHoldingId(h.getId());
        Ledger.Result r = Ledger.replay(ts);
        LocalDate today = LocalDate.now(clock);

        List<TradeView> tradeViews = Ledger.ordered(ts).stream()
                .map(t -> new TradeView(t.getId(), t.getSide(), t.getTradeDate(), t.getShares(), t.getPrice(),
                        t.getAmount(), t.getFee(), t.getTax(), r.realizedByTrade().get(t.getId())))
                .toList();

        Quote q = r.shares() > 0 ? quotes.latestClose(h.getCode(), h.getMarket()).orElse(null) : null;
        BigDecimal price = null;
        LocalDate priceDate = null;
        Long marketValue = null;
        Long unrealized = null;
        BigDecimal unrealizedReturn = null;
        if (q != null) {
            long mv = PnlCalculator.amount(q.price(), r.shares());
            long net = mv - PnlCalculator.fee(mv)
                    - PnlCalculator.tax(h.getCode(), mv, today, taxProperties.bondEtfExemptUntil());
            price = q.price();
            priceDate = q.date();
            marketValue = mv;
            unrealized = net - r.cost();
            unrealizedReturn = PnlCalculator.returnPct(unrealized, r.cost());
        }

        return new PositionView(
                h.getId(), h.getCode(), h.getName(), h.getMarket(), r.shares(), r.cost(), r.gross(),
                r.shares() > 0 ? average(r.gross(), r.shares()) : null,
                r.shares() > 0 ? average(r.cost(), r.shares()) : null,
                r.realized(), tradeViews, price, priceDate, marketValue, unrealized, unrealizedReturn);
    }

    private static BigDecimal average(long total, int shares) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(shares), 2, RoundingMode.HALF_UP);
    }
}
