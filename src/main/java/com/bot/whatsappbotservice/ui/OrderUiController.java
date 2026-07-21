package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.PaymentMethod;
import com.bot.whatsappbotservice.order.PaymentStatus;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.ui.form.OrderForm;
import com.bot.whatsappbotservice.ui.form.OrderStatusForm;
import com.bot.whatsappbotservice.ui.form.PaymentForm;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/orders")
public class OrderUiController {

    private final OrderService orderService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final TenantService tenantService;

    public OrderUiController(OrderService orderService, CustomerService customerService,
                              ProductService productService, TenantService tenantService) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.productService = productService;
        this.tenantService = tenantService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) OrderStatus status,
                        @RequestParam(required = false) PaymentStatus paymentStatus,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                        Model model, Pageable pageable) {
        model.addAttribute("orders", orderService.list(status, paymentStatus, fromDate, toDate, pageable));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("paymentStatus", paymentStatus);
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        DateFilterPresets.addTo(model, DateFilterPresets.resolveZone(tenantService.getCurrent().timezone()));
        return "ui/orders/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        OrderResponse order = orderService.get(id);
        model.addAttribute("order", order);
        model.addAttribute("history", orderService.history(id, PageRequest.of(0, 50)));
        if (!model.containsAttribute("statusForm")) {
            model.addAttribute("statusForm", new OrderStatusForm());
        }
        List<OrderStatus> allowedNextStatuses = Arrays.stream(OrderStatus.values())
                .filter(order.status()::canTransitionTo)
                .toList();
        model.addAttribute("allowedNextStatuses", allowedNextStatuses);
        if (!model.containsAttribute("paymentForm")) {
            PaymentForm paymentForm = new PaymentForm();
            // Pre-fill with the outstanding balance — the common case is settling in full.
            paymentForm.setAmount(order.amountDue());
            model.addAttribute("paymentForm", paymentForm);
        }
        model.addAttribute("paymentMethods", PaymentMethod.values());
        boolean paymentOpen = order.status() != OrderStatus.CANCELLED
                && order.paymentStatus() != PaymentStatus.PAID
                && order.paymentStatus() != PaymentStatus.REFUNDED;
        model.addAttribute("canRecordPayment", paymentOpen);
        model.addAttribute("canRefundPayment", order.paymentStatus() != PaymentStatus.REFUNDED
                && order.amountPaid() != null && order.amountPaid().signum() > 0);
        model.addAttribute("concerns", orderService.listConcerns(id));
        return "ui/orders/detail";
    }

    @PostMapping("/{id}/concerns/{concernId}/resolve")
    public String resolveConcern(@PathVariable Long id, @PathVariable Long concernId,
                                  RedirectAttributes redirectAttributes) {
        orderService.resolveConcern(concernId);
        redirectAttributes.addFlashAttribute("successMessage", "Concern marked as resolved.");
        return "redirect:/ui/orders/" + id;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("orderForm")) {
            model.addAttribute("orderForm", new OrderForm());
        }
        populateFormModel(model);
        return "ui/orders/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("orderForm") OrderForm form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model);
            return "ui/orders/form";
        }
        CreateOrderRequest request = form.toRequest();
        if (request.items().isEmpty()) {
            bindingResult.reject("noItems", "Add at least one product line with a quantity.");
            populateFormModel(model);
            return "ui/orders/form";
        }
        OrderResponse created;
        try {
            created = orderService.createOrder(request);
        } catch (ResourceNotFoundException | BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            populateFormModel(model);
            return "ui/orders/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Order " + created.orderNumber() + " created.");
        return "redirect:/ui/orders/" + created.id();
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @Valid @ModelAttribute("statusForm") OrderStatusForm form,
                                BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please choose a status.");
            return "redirect:/ui/orders/" + id;
        }
        try {
            orderService.updateStatus(id, form.toRequest());
        } catch (BusinessRuleViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/ui/orders/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Order status updated.");
        return "redirect:/ui/orders/" + id;
    }

    @PostMapping("/{id}/payment")
    public String recordPayment(@PathVariable Long id, @Valid @ModelAttribute("paymentForm") PaymentForm form,
                                 BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter a valid payment amount and method.");
            return "redirect:/ui/orders/" + id;
        }
        try {
            orderService.recordPayment(id, form.toRequest());
        } catch (BusinessRuleViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/ui/orders/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Payment recorded.");
        return "redirect:/ui/orders/" + id;
    }

    @PostMapping("/{id}/payment/refund")
    public String refundPayment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.refundPayment(id);
        } catch (BusinessRuleViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/ui/orders/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Payment marked as refunded.");
        return "redirect:/ui/orders/" + id;
    }

    private void populateFormModel(Model model) {
        model.addAttribute("customers", customerService.list(PageRequest.of(0, 200)).getContent());
        model.addAttribute("products", productService.list(null, PageRequest.of(0, 200)).getContent());
    }
}
