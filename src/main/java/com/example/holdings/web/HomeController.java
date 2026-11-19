package com.example.holdings.web;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.holdings.holding.HoldingException;
import com.example.holdings.holding.HoldingInput;
import com.example.holdings.holding.PortfolioQueryService;
import com.example.holdings.holding.PortfolioService;
import com.example.holdings.holding.PositionView;
import com.example.holdings.holding.Trade;
import com.example.holdings.idempotency.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 網頁 2.0。首頁＝部位清單＋總計＋記一筆交易；/holdings/{id}＝單一部位的交易明細，可修改、刪除。
 * 表單送出後一律轉址（POST → redirect → GET）。新增交易的表單帶一次性 token，連按兩次只會記一筆。
 * 修改、刪除帶 version：別的裝置（手機）已經改過這筆，就不蓋掉，提示重新整理。
 */
@Controller
public class HomeController {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;
    private final PortfolioService portfolio;
    private final PortfolioQueryService query;
    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;

    public HomeController(Clock clock, PortfolioService portfolio, PortfolioQueryService query,
                          IdempotencyService idempotency, ObjectMapper mapper) {
        this.clock = clock;
        this.portfolio = portfolio;
        this.query = query;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    private void common(Model model) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        model.addAttribute("now", now.format(FORMAT));
        model.addAttribute("zone", now.getZone().getId());
        model.addAttribute("today", now.toLocalDate().toString());
        model.addAttribute("fmt", Fmt.INSTANCE);
        model.addAttribute("formToken", UUID.randomUUID().toString());
    }

    @GetMapping("/")
    public String home(Model model) {
        common(model);
        var all = query.positions(true);
        model.addAttribute("open", all.stream().filter(PositionView::isOpen).toList());
        model.addAttribute("closed", all.stream().filter(p -> !p.isOpen()).toList());
        model.addAttribute("summary", PortfolioQueryService.summarize(all));
        return "home";
    }

    @GetMapping("/holdings/{id}")
    public String detail(@PathVariable long id, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("p", query.position(id));
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
        common(model);
        return "holding";
    }

    @PostMapping("/trades")
    public String addTrade(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String side,
                           @RequestParam(required = false) String date,
                           @RequestParam(required = false) String shares,
                           @RequestParam(required = false) String price,
                           @RequestParam(required = false) String token,
                           @RequestParam(required = false) String back,
                           RedirectAttributes redirect) {
        String key = "web-" + (token == null || token.isBlank() ? UUID.randomUUID() : token);
        String fp = IdempotencyService.fingerprint("POST", "/trades",
                String.join("|", String.valueOf(code), String.valueOf(side), String.valueOf(date),
                        String.valueOf(shares), String.valueOf(price)));
        Outcome o = run(key, fp, () -> {
            Trade t = portfolio.addTrade(code, side, date, shares, price);
            return ResponseEntity.status(201).body(Map.of("holding_id", t.getHoldingId()));
        });
        if (o.error() != null) {
            redirect.addFlashAttribute("error", o.error());
            redirect.addFlashAttribute("form", new TradeForm(code, side, date, shares, price));
        } else {
            String label = HoldingInput.parseSide(side) == com.example.holdings.pnl.PositionCalculator.Side.BUY ? "買進" : "賣出";
            redirect.addFlashAttribute("message", (o.replayed() ? "這筆已經記過了（重複送出已忽略）：" : "已記錄：")
                    + label + " " + code.trim().toUpperCase() + " " + shares.trim() + " 股");
        }
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/trades/{id}")
    public String updateTrade(@PathVariable long id,
                              @RequestParam(required = false) String date,
                              @RequestParam(required = false) String shares,
                              @RequestParam(required = false) String price,
                              @RequestParam(required = false) String version,
                              @RequestParam(required = false) String back,
                              RedirectAttributes redirect) {
        try {
            portfolio.updateTrade(id, date, shares, price, HoldingInput.parseVersion(version));
            redirect.addFlashAttribute("message", "已修改這筆交易");
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/trades/{id}/delete")
    public String deleteTrade(@PathVariable long id,
                              @RequestParam(required = false) String version,
                              @RequestParam(required = false) String back,
                              RedirectAttributes redirect) {
        try {
            portfolio.deleteTrade(id, HoldingInput.parseVersion(version));
            redirect.addFlashAttribute("message", "已刪除這筆交易");
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        String target = safeBack(back);
        if (target.startsWith("/holdings/")) {
            try {
                query.position(Long.parseLong(target.substring("/holdings/".length())));
            } catch (RuntimeException gone) {
                return "redirect:/"; // 最後一筆刪掉了，部位也不在了
            }
        }
        return "redirect:" + target;
    }

    @PostMapping("/holdings/{id}/delete")
    public String deleteHolding(@PathVariable long id, RedirectAttributes redirect) {
        try {
            portfolio.deleteHolding(id);
            redirect.addFlashAttribute("message", "已刪除這檔的所有紀錄");
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    private record Outcome(String error, boolean replayed) {
    }

    private Outcome run(String key, String fp, Supplier<ResponseEntity<?>> action) {
        try {
            ResponseEntity<String> r = idempotency.execute(key, fp, action);
            boolean replayed = "true".equals(r.getHeaders().getFirst(IdempotencyService.REPLAYED_HEADER));
            if (r.getStatusCode().isError()) {
                return new Outcome(mapper.readTree(r.getBody()).path("error").path("message").asText("發生錯誤"), replayed);
            }
            return new Outcome(null, replayed);
        } catch (HoldingException e) {
            return new Outcome(e.getMessage(), false);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new Outcome("發生錯誤", false);
        }
    }

    /** 只允許轉回本站的首頁或部位頁，不接受任意網址。 */
    private static String safeBack(String back) {
        if (back != null && back.matches("^/holdings/\\d{1,18}$")) {
            return back;
        }
        return "/";
    }

    public record TradeForm(String code, String side, String date, String shares, String price) {
    }
}
