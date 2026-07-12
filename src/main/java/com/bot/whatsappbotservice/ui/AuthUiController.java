package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.auth.AuthService;
import com.bot.whatsappbotservice.auth.PasswordResetRequestResult;
import com.bot.whatsappbotservice.auth.PasswordResetService;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.ui.form.ForgotPasswordForm;
import com.bot.whatsappbotservice.ui.form.RegisterForm;
import com.bot.whatsappbotservice.ui.form.ResetPasswordForm;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui")
public class AuthUiController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthUiController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /** Lets the auth templates word the reset flow for the active delivery mode (on-screen code
     * vs WhatsApp send) without knowing about configuration themselves. */
    @ModelAttribute("onScreenDelivery")
    public boolean onScreenDelivery() {
        return passwordResetService.isOnScreenDelivery();
    }

    @GetMapping("/login")
    public String loginPage() {
        return "ui/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "ui/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "ui/register";
        }
        try {
            authService.register(form.toRequest());
        } catch (DuplicateResourceException e) {
            bindingResult.reject("duplicate", e.getMessage());
            return "ui/register";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Registration successful. Please log in.");
        return "redirect:/ui/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        return "ui/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                                  BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "ui/forgot-password";
        }
        PasswordResetRequestResult result;
        try {
            result = passwordResetService.requestReset(form.getEmail());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("cooldown", e.getMessage());
            return "ui/forgot-password";
        }
        // UNKNOWN_ACCOUNT deliberately falls through to the same neutral message as CODE_SENT so
        // this form can't be used to probe which emails have accounts; the delivery-problem
        // outcomes are shown honestly because a real vendor needs to know no code will arrive.
        switch (result.outcome()) {
            case NO_DELIVERY_CHANNEL -> {
                bindingResult.reject("noDeliveryChannel",
                        "No WhatsApp number is configured for your store, so a reset code cannot be delivered. "
                                + "Please contact support.");
                return "ui/forgot-password";
            }
            case SEND_FAILED -> {
                bindingResult.reject("sendFailed",
                        "We could not deliver the code over WhatsApp. Please try again later or contact support.");
                return "ui/forgot-password";
            }
            default -> {
            }
        }
        ResetPasswordForm resetForm = new ResetPasswordForm();
        resetForm.setEmail(form.getEmail());
        redirectAttributes.addFlashAttribute("resetPasswordForm", resetForm);
        if (result.onScreenCode() != null) {
            redirectAttributes.addFlashAttribute("onScreenCode", result.onScreenCode());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Use the reset code below to set a new password.");
        } else if (passwordResetService.isOnScreenDelivery()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "If an account exists for that email, a reset code has been generated.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "If an account exists for that email, a reset code has been sent to the store's WhatsApp number.");
        }
        return "redirect:/ui/reset-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(Model model) {
        if (!model.containsAttribute("resetPasswordForm")) {
            model.addAttribute("resetPasswordForm", new ResetPasswordForm());
        }
        return "ui/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                 BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("confirmPassword")
                && !Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            return "ui/reset-password";
        }
        try {
            passwordResetService.resetPassword(form.getEmail(), form.getCode(), form.getNewPassword());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("resetFailed", e.getMessage());
            return "ui/reset-password";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Password updated. Please log in with your new password.");
        return "redirect:/ui/login";
    }
}
