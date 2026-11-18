package com.example.holdings.web;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;

    public HomeController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("now", LocalDateTime.now(clock).format(FORMAT));
        return "home";
    }
}
