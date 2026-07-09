package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.storefront.StorefrontCatalogController.ProductLocalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Session-only cart (see {@link StorefrontCart}) — add/remove/view, no checkout logic here. */
@Controller
@RequestMapping("/store/{slug}/cart")
public class StorefrontCartController {

    private static final String SESSION_ATTRIBUTE = "storefrontCart";

    private final ProductService productService;

    public StorefrontCartController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/add")
    public String add(@PathVariable String slug, @RequestParam Long productId, @RequestParam BigDecimal quantity,
                       HttpServletRequest request, RedirectAttributes redirectAttributes) {
        StorefrontCart cart = resolveCart(request);
        BigDecimal existing = cart.lines().getOrDefault(productId, BigDecimal.ZERO);
        cart.setQuantity(productId, existing.add(quantity));
        redirectAttributes.addFlashAttribute("successMessage", "Added to cart.");
        return "redirect:/store/" + slug + "/cart";
    }

    @PostMapping("/remove")
    public String remove(@PathVariable String slug, @RequestParam Long productId, HttpServletRequest request) {
        resolveCart(request).remove(productId);
        return "redirect:/store/" + slug + "/cart";
    }

    @GetMapping
    public String view(@RequestParam(required = false) String lang, HttpServletRequest request,
                        @AuthenticationPrincipal CustomerPrincipal customer, Model model) {
        StorefrontCart cart = resolveCart(request);
        String resolvedLang = resolveLanguage(lang, customer);

        List<CartLineView> lineViews = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : cart.lines().entrySet()) {
            ProductResponse product;
            try {
                product = productService.get(entry.getKey());
            } catch (ResourceNotFoundException e) {
                continue; // product removed/deactivated since it was added — just drop it from the view
            }
            BigDecimal lineTotal = product.price().multiply(entry.getValue());
            total = total.add(lineTotal);
            lineViews.add(new CartLineView(product, entry.getValue(), lineTotal));
        }

        model.addAttribute("lines", lineViews);
        model.addAttribute("total", total);
        model.addAttribute("localization", new ProductLocalizer(resolvedLang));
        return "store/cart";
    }

    private String resolveLanguage(String queryLang, CustomerPrincipal customer) {
        if (StringUtils.hasText(queryLang)) {
            return queryLang;
        }
        return customer != null ? customer.getPreferredLanguageCode() : null;
    }

    static StorefrontCart resolveCart(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        StorefrontCart cart = (StorefrontCart) session.getAttribute(SESSION_ATTRIBUTE);
        if (cart == null) {
            cart = new StorefrontCart();
            session.setAttribute(SESSION_ATTRIBUTE, cart);
        }
        return cart;
    }

    public record CartLineView(ProductResponse product, BigDecimal quantity, BigDecimal lineTotal) {
    }
}
