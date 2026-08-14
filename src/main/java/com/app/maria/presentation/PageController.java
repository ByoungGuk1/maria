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

    @GetMapping("/admin/target-products")
    public String targetProducts(Model model) {
        model.addAttribute("activePath", "/admin/target-products");
        return "target-products";
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

    @GetMapping("/admin/audit-log")
    public String audit(Model model) {
        model.addAttribute("activePath", "/admin/audit-log");
        return "audit-log";
    }

    @GetMapping("/admin/settlement")
    public String settlement(Model model) {
        model.addAttribute("activePath", "/admin/settlement");
        return "settlement";
    }
}
