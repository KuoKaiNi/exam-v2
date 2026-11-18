package com.example.holdings.web;

import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.Side;
import com.example.holdings.service.PositionQueryService;
import com.example.holdings.service.PositionService;
import com.example.holdings.service.PositionView;
import com.example.holdings.service.Totals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 持股網頁：清單、新增第一筆買進、明細（買進、賣出、刪除交易）。 */
@Controller
public class HoldingWebController {

    private final PositionService positions;
    private final PositionQueryService queries;

    public HoldingWebController(PositionService positions, PositionQueryService queries) {
        this.positions = positions;
        this.queries = queries;
    }

    @GetMapping("/holdings")
    public String list(Model model) {
        List<PositionView> list = queries.list();
        model.addAttribute("positions", list);
        model.addAttribute("totals", Totals.of(list));
        return "holdings";
    }

    @PostMapping("/holdings")
    public String create(@RequestParam(defaultValue = "") String code,
                         @RequestParam(defaultValue = "") String tradeDate,
                         @RequestParam(defaultValue = "") String shares,
                         @RequestParam(defaultValue = "") String price,
                         RedirectAttributes redirect) {
        try {
            Long id = positions.createPosition(code, parseDate(tradeDate), parseShares(shares), parsePrice(price));
            return "redirect:/holdings/" + id;
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/holdings";
        }
    }

    @GetMapping("/holdings/{id}")
    public String detail(@PathVariable Long id, Model model) {
        PositionView p = queries.get(id);
        model.addAttribute("p", p);
        model.addAttribute("today", LocalDate.now().toString());
        return "holding-detail";
    }

    @PostMapping("/holdings/{id}/trades")
    public String addTrade(@PathVariable Long id,
                           @RequestParam String side,
                           @RequestParam(defaultValue = "") String tradeDate,
                           @RequestParam(defaultValue = "") String shares,
                           @RequestParam(defaultValue = "") String price,
                           RedirectAttributes redirect) {
        try {
            positions.addTrade(id, parseSide(side), parseDate(tradeDate), parseShares(shares), parsePrice(price));
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/holdings/" + id;
    }

    @PostMapping("/holdings/{id}/trades/{tradeId}/delete")
    public String deleteTrade(@PathVariable Long id, @PathVariable Long tradeId, RedirectAttributes redirect) {
        try {
            positions.deleteTrade(id, tradeId);
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/holdings/" + id;
        }
        return "redirect:/holdings";
    }

    @PostMapping("/holdings/{id}/delete")
    public String delete(@PathVariable Long id) {
        positions.deletePosition(id);
        return "redirect:/holdings";
    }

    /** 查無持股等錯誤：回清單頁並顯示訊息。 */
    @ExceptionHandler(HoldingException.class)
    public String onError(HoldingException e, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", e.getMessage());
        return "redirect:/holdings";
    }

    static int parseShares(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw badInput("股數必須是 1 到 999,999 的整數");
        }
    }

    static BigDecimal parsePrice(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw badInput("買進價必須是數字");
        }
    }

    static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException e) {
            throw badInput("交易日期格式不對");
        }
    }

    static Side parseSide(String raw) {
        try {
            return Side.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw badInput("請選擇買進或賣出");
        }
    }

    private static HoldingException badInput(String message) {
        return new HoldingException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
