package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("productCount", productService.list(null, PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("customerCount", customerService.list(PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("orderCount", orderService.list(null, PageRequest.of(0, 1)).getTotalElements());

        PageRequest recentOrdersRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<OrderResponse> recentOrders = orderService.list(null, recentOrdersRequest).getContent();
        model.addAttribute("recentOrders", recentOrders);

        return "ui/dashboard";
    }
}
