package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.inventory.InventoryService;
import com.bot.whatsappbotservice.ui.form.InventoryAdjustmentForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/inventory")
public class InventoryUiController {

    private final InventoryService inventoryService;

    public InventoryUiController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public String list(Model model, Pageable pageable) {
        model.addAttribute("inventoryItems", inventoryService.listWithProducts(pageable));
        return "ui/inventory/list";
    }

    @GetMapping("/{productId}")
    public String detail(@PathVariable Long productId, Model model) {
        model.addAttribute("inventory", inventoryService.get(productId));
        if (!model.containsAttribute("adjustmentForm")) {
            model.addAttribute("adjustmentForm", new InventoryAdjustmentForm());
        }
        return "ui/inventory/detail";
    }

    @PostMapping("/{productId}/adjust")
    public String adjust(@PathVariable Long productId,
                          @Valid @ModelAttribute("adjustmentForm") InventoryAdjustmentForm form,
                          BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("inventory", inventoryService.get(productId));
            return "ui/inventory/detail";
        }
        try {
            inventoryService.adjustStock(productId, form.toRequest());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            model.addAttribute("inventory", inventoryService.get(productId));
            return "ui/inventory/detail";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Stock adjusted.");
        return "redirect:/ui/inventory/" + productId;
    }

    @GetMapping("/{productId}/history")
    public String history(@PathVariable Long productId, Model model, Pageable pageable) {
        model.addAttribute("inventory", inventoryService.get(productId));
        model.addAttribute("transactions", inventoryService.history(productId, pageable));
        return "ui/inventory/history";
    }
}
