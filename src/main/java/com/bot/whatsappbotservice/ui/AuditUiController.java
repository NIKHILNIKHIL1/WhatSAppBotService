package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.audit.AuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/ui/audit")
@PreAuthorize("hasRole('VENDOR_ADMIN')")
public class AuditUiController {

    private final AuditService auditService;

    public AuditUiController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String entityName, Model model, Pageable pageable) {
        model.addAttribute("logs", auditService.list(entityName, pageable));
        model.addAttribute("entityName", entityName);
        return "ui/audit/list";
    }
}
