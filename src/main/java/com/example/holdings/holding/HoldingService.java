package com.example.holdings.holding;

import java.time.Clock;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.holdings.quote.QuoteService;

@Service
public class HoldingService {

    private final HoldingRepository repository;
    private final QuoteService quoteService;
    private final Clock clock;

    public HoldingService(HoldingRepository repository, QuoteService quoteService, Clock clock) {
        this.repository = repository;
        this.quoteService = quoteService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Holding> list() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Holding get(long id) {
        return repository.findById(id).orElseThrow(() -> HoldingException.notFound(id));
    }

    public Holding create(String rawCode, String rawLots, String rawPrice) {
        String code = HoldingInput.parseCode(rawCode);
        HoldingInput.Values values = HoldingInput.parseValues(rawLots, rawPrice);
        if (repository.existsByCode(code)) {
            throw duplicate(code);
        }
        // 查官方確認代號存在（上市或上櫃），查不到就丟 STOCK_NOT_FOUND，不儲存
        quoteService.lookupAndCache(code);
        try {
            return repository.saveAndFlush(new Holding(code, values.lots(), values.buyPrice(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // 兩個請求同時新增同一代號時，由資料庫唯一索引擋下
            throw duplicate(code);
        }
    }

    @Transactional
    public Holding update(long id, String rawLots, String rawPrice) {
        Holding holding = repository.findById(id).orElseThrow(() -> HoldingException.notFound(id));
        HoldingInput.Values values = HoldingInput.parseValues(rawLots, rawPrice);
        holding.update(values.lots(), values.buyPrice(), clock.instant());
        return holding;
    }

    @Transactional
    public void delete(long id) {
        Holding holding = repository.findById(id).orElseThrow(() -> HoldingException.notFound(id));
        repository.delete(holding);
    }

    private static HoldingException duplicate(String code) {
        return new HoldingException(HoldingException.Kind.DUPLICATE_CODE, code + " 已在持股中");
    }
}
