package com.example.holdings.service;

import com.example.holdings.domain.Holding;
import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.HoldingRepository;
import com.example.holdings.domain.HoldingRules;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldingService {

    private final HoldingRepository repository;

    public HoldingService(HoldingRepository repository) {
        this.repository = repository;
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
        try {
            return repository.saveAndFlush(new Holding(code, lots, buyPrice));
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

    static HoldingException notFound() {
        return new HoldingException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到這筆持股");
    }

    static HoldingException duplicate(String code) {
        return new HoldingException(HttpStatus.CONFLICT, "DUPLICATE_CODE", code + " 已在持股中");
    }
}
