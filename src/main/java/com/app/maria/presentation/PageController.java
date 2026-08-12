package com.app.maria.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public String dashboard(Model model) {
        model.addAttribute("activePath", "/admin/dashboard");
        return "dashboard";
    }

    @GetMapping("/admin/sell-orders")
    public String sellOrders(Model model) {
        model.addAttribute("activePath", "/admin/sell-orders");
        return "sellOrder";
    }

    @GetMapping("/admin/account")
    public String account(Model model) {
        model.addAttribute("activePath", "/admin/account");
        return "account-management";
    }
}
