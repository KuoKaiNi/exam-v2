package com.example.holdings.web;

import com.example.holdings.domain.Holding;
import com.example.holdings.domain.HoldingException;
import com.example.holdings.domain.HoldingRules;
import com.example.holdings.service.HoldingQueryService;
import com.example.holdings.service.HoldingService;
import com.example.holdings.service.HoldingView;
import com.example.holdings.service.Summary;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 持股清單與表單頁（Thymeleaf）。 */
@Controller
public class HoldingWebController {

    private final HoldingService service;
    private final HoldingQueryService queryService;

    public HoldingWebController(HoldingService service, HoldingQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/holdings")
    public String list(Model model) {
        fillList(model, queryService.list(), null, new HoldingForm("", "", ""));
        return "holdings";
    }

    @PostMapping("/holdings")
    public String create(@RequestParam(defaultValue = "") String code,
                         @RequestParam(defaultValue = "") String lots,
                         @RequestParam(defaultValue = "") String buyPrice,
                         Model model) {
        HoldingForm form = new HoldingForm(code, lots, buyPrice);
        try {
            service.create(code, parseLots(lots), parseBuyPrice(buyPrice));
        } catch (HoldingException e) {
            fillList(model, queryService.list(), e.getMessage(), form);
            return "holdings";
        }
        return "redirect:/holdings";
    }

    @GetMapping("/holdings/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Holding holding = service.get(id);
        model.addAttribute("holding", holding);
        model.addAttribute("form", new HoldingForm(holding.getCode(),
                String.valueOf(holding.getLots()), holding.getBuyPrice().toPlainString()));
        return "holding-edit";
    }

    @PostMapping("/holdings/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String lots,
                         @RequestParam(defaultValue = "") String buyPrice,
                         Model model) {
        try {
            service.update(id, parseLots(lots), parseBuyPrice(buyPrice));
        } catch (HoldingException e) {
            Holding holding = service.get(id);
            model.addAttribute("holding", holding);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", new HoldingForm(holding.getCode(), lots, buyPrice));
            return "holding-edit";
        }
        return "redirect:/holdings";
    }

    @PostMapping("/holdings/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/holdings";
    }

    /** 找不到持股等錯誤：回清單頁並顯示訊息（例如舊的修改連結）。 */
    @ExceptionHandler(HoldingException.class)
    public String onError(HoldingException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/holdings";
    }

    private void fillList(Model model, List<HoldingView> holdings, String error, HoldingForm form) {
        model.addAttribute("holdings", holdings);
        model.addAttribute("summary", Summary.of(holdings));
        if (error != null) {
            model.addAttribute("error", error);
        }
        model.addAttribute("form", form);
    }

    static Integer parseLots(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new HoldingException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "張數必須是 1 到 999 的整數");
        }
    }

    static BigDecimal parseBuyPrice(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new HoldingException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "買進價必須是數字");
        }
    }

    /** 表單回填用，錯誤時保留使用者輸入的值。 */
    public record HoldingForm(String code, String lots, String buyPrice) {
    }
}
