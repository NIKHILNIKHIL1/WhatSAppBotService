package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.inventory.InventoryService;
import com.bot.whatsappbotservice.ui.form.InventoryAdjustmentForm;
import com.bot.whatsappbotservice.ui.form.ReorderLevelForm;
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
        var inventory = inventoryService.get(productId);
        model.addAttribute("inventory", inventory);
        if (!model.containsAttribute("adjustmentForm")) {
            model.addAttribute("adjustmentForm", new InventoryAdjustmentForm());
        }
        if (!model.containsAttribute("reorderLevelForm")) {
            ReorderLevelForm reorderLevelForm = new ReorderLevelForm();
            reorderLevelForm.setReorderLevel(inventory.reorderLevel());
            model.addAttribute("reorderLevelForm", reorderLevelForm);
        }
        return "ui/inventory/detail";
    }

    @PostMapping("/{productId}/reorder-level")
    public String updateReorderLevel(@PathVariable Long productId,
                                      @Valid @ModelAttribute("reorderLevelForm") ReorderLevelForm form,
                                      BindingResult bindingResult, Model model,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            repopulateDetailModel(productId, model);
            return "ui/inventory/detail";
        }
        inventoryService.updateReorderLevel(productId, form.getReorderLevel());
        redirectAttributes.addFlashAttribute("successMessage",
                "Reorder level updated. You'll get a WhatsApp alert when stock drops to this level.");
        return "redirect:/ui/inventory/" + productId;
    }

    @PostMapping("/{productId}/adjust")
    public String adjust(@PathVariable Long productId,
                          @Valid @ModelAttribute("adjustmentForm") InventoryAdjustmentForm form,
                          BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            repopulateDetailModel(productId, model);
            return "ui/inventory/detail";
        }
        try {
            inventoryService.adjustStock(productId, form.toRequest());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            repopulateDetailModel(productId, model);
            return "ui/inventory/detail";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Stock adjusted.");
        return "redirect:/ui/inventory/" + productId;
    }

    /** The detail template binds both forms; any error re-render must supply whichever form the
     * failed POST didn't bind, or Thymeleaf blows up on the missing model attribute. */
    private void repopulateDetailModel(Long productId, Model model) {
        var inventory = inventoryService.get(productId);
        model.addAttribute("inventory", inventory);
        if (!model.containsAttribute("adjustmentForm")) {
            model.addAttribute("adjustmentForm", new InventoryAdjustmentForm());
        }
        if (!model.containsAttribute("reorderLevelForm")) {
            ReorderLevelForm reorderLevelForm = new ReorderLevelForm();
            reorderLevelForm.setReorderLevel(inventory.reorderLevel());
            model.addAttribute("reorderLevelForm", reorderLevelForm);
        }
    }

    @GetMapping("/{productId}/history")
    public String history(@PathVariable Long productId, Model model, Pageable pageable) {
        model.addAttribute("inventory", inventoryService.get(productId));
        model.addAttribute("transactions", inventoryService.history(productId, pageable));
        return "ui/inventory/history";
    }
}
