package com.example.holdings.service;

import com.example.holdings.domain.Holding;
import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.HoldingRepository;
import com.example.holdings.domain.HoldingRules;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteService.StockInfo;
import com.example.holdings.quote.SourceUnavailableException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldingService {

    private final HoldingRepository repository;
    private final QuoteService quoteService;

    public HoldingService(HoldingRepository repository, QuoteService quoteService) {
        this.repository = repository;
        this.quoteService = quoteService;
    }

    @Transactional(readOnly = true)
    public List<Holding> list() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Holding get(Long id) {
        return repository.findById(id).orElseThrow(HoldingService::notFound);
    }

    @Transactional
    public Holding create(String rawCode, Integer lots, BigDecimal buyPrice) {
        String code = HoldingRules.normalizeCode(rawCode);
        HoldingRules.checkLots(lots);
        HoldingRules.checkBuyPrice(buyPrice);
        if (repository.existsByCode(code)) {
            throw duplicate(code);
        }
        StockInfo info = lookupOfficial(code);
        try {
            return repository.saveAndFlush(new Holding(code, info.name(), info.market(), lots, buyPrice));
        } catch (DataIntegrityViolationException e) {
            // 同時送出兩筆相同代號時，由資料庫唯一鍵擋下
            throw duplicate(code);
        }
    }

    @Transactional
    public Holding update(Long id, Integer lots, BigDecimal buyPrice) {
        HoldingRules.checkLots(lots);
        HoldingRules.checkBuyPrice(buyPrice);
        Holding holding = get(id);
        holding.changeLotsAndPrice(lots, buyPrice);
        return holding;
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }

    /** 上市、上櫃都查不到就不存；官方連不上則回 503，不把未確認的代號存進去。 */
    private StockInfo lookupOfficial(String code) {
        try {
            return quoteService.lookup(code).orElseThrow(() -> new HoldingException(
                    HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "找不到代號 " + code + "，上市、上櫃都查不到"));
        } catch (SourceUnavailableException e) {
            throw new HoldingException(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_UNAVAILABLE",
                    "官方行情暫時連不上，請稍後再試");
        }
    }

    static HoldingException notFound() {
        return new HoldingException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到這筆持股");
    }

    static HoldingException duplicate(String code) {
        return new HoldingException(HttpStatus.CONFLICT, "DUPLICATE_CODE", code + " 已在持股中");
    }
}
