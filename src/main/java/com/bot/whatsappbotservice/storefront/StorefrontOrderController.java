package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Checkout and order history — both require an authenticated {@link CustomerPrincipal} (enforced
 * by the {@code /store/{slug}/checkout} and {@code /store/{slug}/orders/**} authorization rules in
 * {@code SecurityConfig}), so {@code customerId} always comes from the session principal, never
 * from client-submitted form data — a customer can never place an order or read history as
 * someone else this way.
 *
 * <p>No per-order detail-by-id route: {@code OrderService.get(id)} is tenant-scoped but not
 * customer-scoped, so exposing it here would let one customer view another's order by guessing an
 * id. The list view alone (via {@link OrderService#listForCustomer}) is enough for v1.
 */
@Controller
@RequestMapping("/store/{slug}")
public class StorefrontOrderController {

    private final OrderService orderService;

    public StorefrontOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public String checkout(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal customer,
                            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        StorefrontCart cart = StorefrontCartController.resolveCart(request);
        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your cart is empty.");
            return "redirect:/store/" + slug + "/cart";
        }

        List<OrderItemRequest> items = cart.lines().entrySet().stream()
                .map(entry -> new OrderItemRequest(entry.getKey(), entry.getValue()))
                .toList();
        CreateOrderRequest orderRequest =
                new CreateOrderRequest(customer.getCustomerId(), OrderChannel.CUSTOMER_WEB, items, null, null);

        OrderResponse order;
        try {
            order = orderService.createOrder(orderRequest);
        } catch (ResourceNotFoundException | BusinessRuleViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't place your order: " + e.getMessage());
            return "redirect:/store/" + slug + "/cart";
        }

        cart.clear();
        redirectAttributes.addFlashAttribute("successMessage",
                "Order " + order.orderNumber() + " placed! Total: " + order.totalAmount() + " " + order.currencyCode());
        return "redirect:/store/" + slug + "/orders";
    }

    @GetMapping("/orders")
    public String orders(@AuthenticationPrincipal CustomerPrincipal customer, Model model) {
        Page<OrderResponse> orders = orderService.listForCustomer(customer.getCustomerId(), PageRequest.of(0, 50));
        model.addAttribute("orders", orders);
        return "store/orders";
    }
}
