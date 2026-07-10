package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/ui/dashboard")
public class DashboardUiController {

    private final ProductService productService;
    private final OrderService orderService;
    private final CustomerService customerService;

    public DashboardUiController(ProductService productService, OrderService orderService,
                                  CustomerService customerService) {
        this.productService = productService;
        this.orderService = orderService;
        this.customerService = customerService;
    }

    /**
     * Without dates this is the classic all-time dashboard. With a date filter (picker or one of
     * the preset chips) the orders table narrows to that period and a period summary (order count
     * + revenue) appears; the all-time tiles stay put so the page never loses its bearings.
     */
    @GetMapping
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        model.addAttribute("productCount", productService.list(null, PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("customerCount", customerService.list(PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("orderCount", orderService.list(null, PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("outstandingPayments", orderService.outstandingPayments());

        boolean dateFiltered = fromDate != null || toDate != null;
        PageRequest recentOrdersRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponse> ordersPage = orderService.list(null, null, fromDate, toDate, recentOrdersRequest);
        List<OrderResponse> recentOrders = ordersPage.getContent();
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("dateFiltered", dateFiltered);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        DateFilterPresets.addTo(model);

        if (dateFiltered) {
            // Whole-day defaults for a half-open input: only "from" means from-that-day-onward,
            // so the summary runs to today; only "to" means everything up to that day.
            LocalDate summaryFrom = fromDate != null ? fromDate : LocalDate.EPOCH;
            LocalDate summaryTo = toDate != null ? toDate : LocalDate.now();
            model.addAttribute("periodOrderCount", ordersPage.getTotalElements());
            model.addAttribute("periodRevenue", orderService.revenueBetween(summaryFrom, summaryTo));
        }

        return "ui/dashboard";
    }
}
