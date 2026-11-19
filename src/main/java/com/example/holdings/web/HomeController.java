package com.example.holdings.web;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.holdings.holding.Holding;
import com.example.holdings.holding.HoldingException;
import com.example.holdings.holding.HoldingService;

/**
 * 首頁＝持股清單。表單送出後一律轉回首頁（POST → redirect → GET），錯誤訊息用 flash 帶回。
 */
@Controller
public class HomeController {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;
    private final HoldingService holdingService;

    public HomeController(Clock clock, HoldingService holdingService) {
        this.clock = clock;
        this.holdingService = holdingService;
    }

    @GetMapping("/")
    public String home(Model model) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        model.addAttribute("now", now.format(FORMAT));
        model.addAttribute("zone", now.getZone().getId());
        model.addAttribute("holdings", holdingService.list());
        return "home";
    }

    @PostMapping("/holdings")
    public String add(@RequestParam(required = false) String code,
                      @RequestParam(required = false) String lots,
                      @RequestParam(required = false) String buyPrice,
                      RedirectAttributes redirect) {
        try {
            Holding h = holdingService.create(code, lots, buyPrice);
            redirect.addFlashAttribute("message", "已新增 " + h.getCode());
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("form", new AddForm(code, lots, buyPrice));
        }
        return "redirect:/";
    }

    @PostMapping("/holdings/{id}")
    public String update(@PathVariable long id,
                         @RequestParam(required = false) String lots,
                         @RequestParam(required = false) String buyPrice,
                         RedirectAttributes redirect) {
        try {
            Holding h = holdingService.update(id, lots, buyPrice);
            redirect.addFlashAttribute("message", "已修改 " + h.getCode());
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/holdings/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            holdingService.delete(id);
            redirect.addFlashAttribute("message", "已刪除");
        } catch (HoldingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    public record AddForm(String code, String lots, String buyPrice) {
    }
}
