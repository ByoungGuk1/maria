package com.app.maria.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/admin/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/admin/sell-orders")
    public String sellOrders() {
        return "sellOrder";
    }

}
