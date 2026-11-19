package com.example.holdings.holding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.holdings.pnl.OversellException;
import com.example.holdings.pnl.PositionCalculator;
import com.example.holdings.pnl.PositionCalculator.Side;
import com.example.holdings.pnl.TaxRules;
import com.example.holdings.quote.QuoteService;

/**
 * 寫入：新增／修改／刪除交易與部位。
 *
 * <p>並行控制：
 * <ul>
 * <li>每次異動交易都在同一個交易（transaction）裡先鎖住部位那一列（SELECT ... FOR UPDATE），重新讀出全部交易、
 *     套上這次的變動、用 {@link PositionCalculator} 從頭重算；任一時點股數變負就整個取消。
 *     所以手機和電腦同時賣出，不會兩邊都通過檢查而賣超。</li>
 * <li>修改、刪除交易可以帶 version（樂觀鎖）：拿舊畫面的資料來改會回 VERSION_CONFLICT，不會蓋掉別的裝置剛改的內容。</li>
 * <li>新代號第一次買進時要先查官方（最多約 9 秒），不在資料庫交易裡等網路；建立部位撞到代號唯一索引（另一台裝置剛建好）就沿用那一筆。</li>
 * </ul>
 */
@Service
public class PortfolioService {

    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final QuoteService quoteService;
    private final TaxRules taxRules;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;

    public PortfolioService(HoldingRepository holdings, TradeRepository trades, QuoteService quoteService,
                            TaxRules taxRules, Clock clock, PlatformTransactionManager txManager) {
        this.holdings = holdings;
        this.trades = trades;
        this.quoteService = quoteService;
        this.taxRules = taxRules;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------------------------------------------------------------- 2.0

    /** 新增一筆買進或賣出。買進新代號會自動建立部位（先確認代號存在）。 */
    public Trade addTrade(String rawCode, String rawSide, String rawDate, String rawShares, String rawPrice) {
        String code = HoldingInput.parseCode(rawCode);
        Side side = HoldingInput.parseSide(rawSide);
        List<String> errors = new ArrayList<>();
        LocalDate date = collect(errors, () -> HoldingInput.parseDate(rawDate, today()));
        Long shares = collect(errors, () -> HoldingInput.parseShares(rawShares));
        BigDecimal price = collect(errors, () -> HoldingInput.parseTradePrice(rawPrice));
        if (!errors.isEmpty()) {
            throw HoldingException.validation(String.join("；", errors));
        }
        return addTrade(code, side, date, false, shares, price, false);
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse();
    }

    private static <T> T collect(List<String> errors, Parser<T> p) {
        try {
            return p.parse();
        } catch (HoldingException e) {
            errors.add(e.getMessage());
            return null;
        }
    }

    /**
     * @param requireNoOpenPosition App 1.0 的「新增持股」：這檔已經有持股（股數 &gt; 0）就回 DUPLICATE_CODE
     */
    private Trade addTrade(String code, Side side, LocalDate date, boolean dateEstimated, long shares,
                           BigDecimal price, boolean requireNoOpenPosition) {
        Optional<Holding> existing = holdings.findByCode(code);
        long holdingId;
        if (existing.isPresent()) {
            holdingId = existing.get().getId();
        } else if (side == Side.SELL) {
            throw new HoldingException(HoldingException.Kind.INSUFFICIENT_SHARES, "沒有 " + code + " 的持股，不能賣出");
        } else {
            quoteService.lookupAndCache(code); // 查無代號丟 STOCK_NOT_FOUND；網路在資料庫交易之外
            holdingId = createHoldingIfAbsent(code);
        }
        return tx.execute(status -> {
            Holding h = holdings.lockById(holdingId)
                    .orElseThrow(() -> new HoldingException(HoldingException.Kind.VERSION_CONFLICT,
                            code + " 的持股剛被刪除，請重新整理後再試"));
            List<Trade> current = trades.findByHoldingId(holdingId);
            if (requireNoOpenPosition && compute(h, current).shares() > 0) {
                throw new HoldingException(HoldingException.Kind.DUPLICATE_CODE, code + " 已在持股中");
            }
            Trade t = new Trade(holdingId, side, date, dateEstimated, shares, price, clock.instant());
            List<Trade> after = new ArrayList<>(current);
            after.add(t);
            compute(h, after); // 超賣丟例外，整個交易取消
            h.touch(clock.instant());
            return trades.save(t);
        });
    }

    private long createHoldingIfAbsent(String code) {
        try {
            return newTx.execute(s -> holdings.saveAndFlush(new Holding(code, clock.instant())).getId());
        } catch (DataIntegrityViolationException e) {
            // 另一台裝置同時建好了同一個代號
            return holdings.findByCode(code).orElseThrow(() -> e).getId();
        }
    }

    /** 修改一筆交易（方向不能改）。expectedVersion 不為 null 時要跟資料庫一致。 */
    public Trade updateTrade(long tradeId, String rawDate, String rawShares, String rawPrice, Long expectedVersion) {
        Trade existing = trades.findById(tradeId).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
        List<String> errors = new ArrayList<>();
        LocalDate date = collect(errors, () -> HoldingInput.parseDate(rawDate, today()));
        Long shares = collect(errors, () -> HoldingInput.parseShares(rawShares));
        BigDecimal price = collect(errors, () -> HoldingInput.parseTradePrice(rawPrice));
        if (!errors.isEmpty()) {
            throw HoldingException.validation(String.join("；", errors));
        }
        return tx.execute(status -> {
            Holding h = holdings.lockById(existing.getHoldingId()).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
            Trade t = trades.findById(tradeId).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
            checkVersion(t, expectedVersion);
            boolean estimated = t.isDateEstimated() && date.equals(t.getTradeDate());
            t.update(date, estimated, shares, price, clock.instant());
            compute(h, trades.findByHoldingId(h.getId()));
            h.touch(clock.instant());
            return trades.saveAndFlush(t);
        });
    }

    /** 刪除一筆交易。部位沒有任何交易了就連部位一起刪掉。 */
    public void deleteTrade(long tradeId, Long expectedVersion) {
        Trade existing = trades.findById(tradeId).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
        tx.executeWithoutResult(status -> {
            Holding h = holdings.lockById(existing.getHoldingId()).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
            Trade t = trades.findById(tradeId).orElseThrow(() -> HoldingException.tradeNotFound(tradeId));
            checkVersion(t, expectedVersion);
            List<Trade> after = new ArrayList<>(trades.findByHoldingId(h.getId()));
            after.removeIf(x -> x.getId().equals(tradeId));
            compute(h, after); // 刪掉買進可能讓之後的賣出變成超賣
            trades.delete(t);
            if (after.isEmpty()) {
                holdings.delete(h);
            } else {
                h.touch(clock.instant());
            }
        });
    }

    /** 刪除整個部位（含所有交易）。 */
    public void deleteHolding(long holdingId) {
        tx.executeWithoutResult(status -> {
            Holding h = holdings.lockById(holdingId).orElseThrow(() -> HoldingException.notFound(holdingId));
            trades.deleteAll(trades.findByHoldingId(holdingId));
            holdings.delete(h);
        });
    }

    private static void checkVersion(Trade t, Long expected) {
        if (expected != null && expected != t.getVersion()) {
            throw new HoldingException(HoldingException.Kind.VERSION_CONFLICT,
                    "這筆交易已經在別的裝置被修改過，請重新整理後再改");
        }
    }

    private PositionCalculator.Position compute(Holding h, List<Trade> list) {
        try {
            return PositionCalculator.compute(h.getCode(), list.stream().map(Trade::toInput).toList(), taxRules);
        } catch (OversellException e) {
            String hint = list.stream().anyMatch(t -> t.isDateEstimated() && t.getSide() == Side.BUY
                    && t.getTradeDate().isAfter(e.getDate()))
                    ? "。這檔有從 1.0 轉入的買進，日期是記錄當天、不是實際買進日，請先把那筆的日期改成實際買進日"
                    : "";
            throw new HoldingException(HoldingException.Kind.INSUFFICIENT_SHARES, h.getCode() + "：" + e.getMessage() + hint);
        }
    }

    // ---------------------------------------------------------------- App 1.0 相容

    /**
     * App 1.0 的「新增持股」＝記一筆今天的買進（日期標記為推估）。
     * 這檔已有持股 → DUPLICATE_CODE；這檔以前全部賣光了 → 在原部位再記一筆買進。
     */
    public Holding v1Create(String rawCode, String rawLots, String rawPrice) {
        String code = HoldingInput.parseCode(rawCode);
        HoldingInput.Values v = HoldingInput.parseValues(rawLots, rawPrice);
        Trade t = addTrade(code, Side.BUY, today(), true, (long) v.lots() * 1000, v.buyPrice(), true);
        return holdings.findById(t.getHoldingId()).orElseThrow();
    }

    /**
     * App 1.0 的「修改」：只有一筆買進、沒有其他交易時，才改那一筆的股數與價格。
     * 2.0 已經有多筆買賣的部位，1.0 的「張數＋買進價」表達不了，回 HAS_TRADE_HISTORY，不動資料。
     */
    public Holding v1Update(long holdingId, String rawLots, String rawPrice) {
        Holding found = v1Find(holdingId);
        HoldingInput.Values v = HoldingInput.parseValues(rawLots, rawPrice);
        return tx.execute(status -> {
            Holding h = holdings.lockById(found.getId()).orElseThrow(() -> HoldingException.notFound(holdingId));
            List<Trade> list = trades.findByHoldingId(h.getId());
            if (list.size() != 1 || list.get(0).getSide() != Side.BUY) {
                throw new HoldingException(HoldingException.Kind.HAS_TRADE_HISTORY,
                        h.getCode() + " 有多筆買賣紀錄，1.0 版無法修改，請到網頁或更新 App 後修改");
            }
            Trade t = list.get(0);
            t.update(t.getTradeDate(), t.isDateEstimated(), (long) v.lots() * 1000, v.buyPrice(), clock.instant());
            h.touch(clock.instant());
            trades.saveAndFlush(t);
            return h;
        });
    }

    /** App 1.0 的「刪除」＝刪除整個部位。 */
    public void v1Delete(long holdingId) {
        deleteHolding(v1Find(holdingId).getId());
    }

    /** App 1.0 看得到的部位：存在而且還有股數。全部賣光的部位對 1.0 來說等於不存在。 */
    public Holding v1Find(long holdingId) {
        Holding h = holdings.findById(holdingId).orElseThrow(() -> HoldingException.notFound(holdingId));
        if (compute(h, trades.findByHoldingId(holdingId)).shares() == 0) {
            throw HoldingException.notFound(holdingId);
        }
        return h;
    }
}
