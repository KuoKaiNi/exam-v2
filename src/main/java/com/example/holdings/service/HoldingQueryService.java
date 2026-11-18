package com.example.holdings.service;

import com.example.holdings.domain.Holding;
import com.example.holdings.domain.HoldingRepository;
import com.example.holdings.domain.PnlCalculator;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteService.Quote;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldingQueryService {

    private final HoldingRepository repository;
    private final QuoteService quoteService;

    public HoldingQueryService(HoldingRepository repository, QuoteService quoteService) {
        this.repository = repository;
        this.quoteService = quoteService;
    }

    @Transactional(readOnly = true)
    public List<HoldingView> list() {
        return repository.findAllByOrderByIdAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public HoldingView get(Long id) {
        return repository.findById(id).map(this::toView).orElseThrow(HoldingService::notFound);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return Summary.of(list());
    }

    private HoldingView toView(Holding h) {
        Quote quote = quoteService.latestClose(h.getCode(), h.getMarket()).orElse(null);
        if (quote == null) {
            return new HoldingView(h.getId(), h.getCode(), h.getName(), h.getMarket(), h.getLots(),
                    h.getBuyPrice(), null, null,
                    PnlCalculator.buyCost(h.getLots(), h.getBuyPrice()), null, null, null);
        }
        PnlCalculator.Result r = PnlCalculator.calculate(h.getCode(), h.getLots(), h.getBuyPrice(), quote.price());
        return new HoldingView(h.getId(), h.getCode(), h.getName(), h.getMarket(), h.getLots(),
                h.getBuyPrice(), quote.price(), quote.date(), r.buyCost(), r.marketValue(), r.pnl(), r.returnPct());
    }
}
