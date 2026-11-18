package com.example.holdings.service;

import com.example.holdings.domain.Holding;
import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.HoldingRepository;
import com.example.holdings.domain.HoldingRules;
import com.example.holdings.domain.Ledger;
import com.example.holdings.domain.PnlCalculator;
import com.example.holdings.domain.Side;
import com.example.holdings.domain.TaxProperties;
import com.example.holdings.domain.Trade;
import com.example.holdings.domain.TradeRepository;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteService.StockInfo;
import com.example.holdings.quote.SourceUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 所有寫入。每個方法先拿寫入鎖，再檢查、再寫；帳本驗證失敗就整筆回滾。 */
@Service
public class PositionService {

    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final QuoteService quoteService;
    private final WriteLockService lock;
    private final TaxProperties taxProperties;
    private final Clock clock;

    public PositionService(HoldingRepository holdings, TradeRepository trades, QuoteService quoteService,
                           WriteLockService lock, TaxProperties taxProperties, Clock clock) {
        this.holdings = holdings;
        this.trades = trades;
        this.quoteService = quoteService;
        this.lock = lock;
        this.taxProperties = taxProperties;
        this.clock = clock;
    }

    /** 新增一檔（第一筆買進）。date 為 null 代表 1.0 來的資料（沒有日期）。 */
    @Transactional
    public Long createPosition(String rawCode, LocalDate date, int shares, BigDecimal price) {
        lock.acquire();
        String code = HoldingRules.normalizeCode(rawCode);
        HoldingRules.checkShares(shares);
        HoldingRules.checkBuyPrice(price);
        if (date != null) {
            HoldingRules.checkTradeDate(date, today());
        }
        if (holdings.existsByCode(code)) {
            throw duplicate(code);
        }
        StockInfo info = lookupOfficial(code);
        Holding h = holdings.save(new Holding(code, info.name(), info.market()));
        long amount = PnlCalculator.amount(price, shares);
        trades.save(new Trade(h.getId(), Side.BUY, date, shares, price, amount, PnlCalculator.fee(amount), 0));
        return h.getId();
    }

    /** 新增一筆買進或賣出。賣出後帳本若不成立（超過持股），整筆回滾。 */
    @Transactional
    public Long addTrade(Long holdingId, Side side, LocalDate date, int shares, BigDecimal price) {
        lock.acquire();
        Holding h = findOrThrow(holdingId);
        HoldingRules.checkTradeDate(date, today());
        HoldingRules.checkShares(shares);
        HoldingRules.checkBuyPrice(price);
        long amount = PnlCalculator.amount(price, shares);
        long fee = PnlCalculator.fee(amount);
        long tax = side == Side.SELL
                ? PnlCalculator.tax(h.getCode(), amount, date, taxProperties.bondEtfExemptUntil())
                : 0;
        Trade saved = trades.save(new Trade(h.getId(), side, date, shares, price, amount, fee, tax));
        Ledger.replay(trades.findByHoldingId(h.getId()));
        return saved.getId();
    }

    /** 刪除一筆交易。刪完若之後的賣出會超過持股，回 409 並回滾；全部刪光則連同部位一起刪除。 */
    @Transactional
    public void deleteTrade(Long holdingId, Long tradeId) {
        lock.acquire();
        Holding h = findOrThrow(holdingId);
        Trade t = trades.findById(tradeId)
                .filter(x -> x.getHoldingId().equals(holdingId))
                .orElseThrow(() -> new HoldingException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到這筆交易"));
        trades.delete(t);
        trades.flush();
        List<Trade> rest = trades.findByHoldingId(holdingId);
        if (rest.isEmpty()) {
            holdings.delete(h);
            return;
        }
        try {
            Ledger.replay(rest);
        } catch (HoldingException e) {
            throw new HoldingException(HttpStatus.CONFLICT, "TRADE_CONFLICT",
                    "刪除後，之後的賣出會超過持股，無法刪除這筆交易");
        }
    }

    @Transactional
    public void deletePosition(Long id) {
        lock.acquire();
        Holding h = findOrThrow(id);
        trades.deleteAll(trades.findByHoldingId(id));
        holdings.delete(h);
    }

    /** 1.0 的修改：只能改整張、單一買進的持股。 */
    @Transactional
    public void updateSingleBuy(Long id, int shares, BigDecimal price) {
        lock.acquire();
        Holding h = findOrThrow(id);
        List<Trade> ts = trades.findByHoldingId(id);
        if (ts.size() != 1 || ts.get(0).getSide() != Side.BUY) {
            throw new HoldingException(HttpStatus.CONFLICT, "MULTI_TRADE",
                    h.getCode() + " 已有多筆買賣，請用 2.0 版或網頁版修改");
        }
        HoldingRules.checkShares(shares);
        HoldingRules.checkBuyPrice(price);
        long amount = PnlCalculator.amount(price, shares);
        ts.get(0).changeBuy(shares, price, amount, PnlCalculator.fee(amount));
    }

    private Holding findOrThrow(Long id) {
        return holdings.findById(id).orElseThrow(PositionService::notFound);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
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

    public static HoldingException notFound() {
        return new HoldingException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到這筆持股");
    }

    public static HoldingException duplicate(String code) {
        return new HoldingException(HttpStatus.CONFLICT, "DUPLICATE_CODE", code + " 已在持股中");
    }
}
