package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.catalog.Category;
import com.bot.whatsappbotservice.catalog.CategoryRepository;
import com.bot.whatsappbotservice.catalog.Product;
import com.bot.whatsappbotservice.catalog.ProductRepository;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListRow;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListSection;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ReplyButton;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the WhatsApp ordering flow (language -> category -> product -> quantity -> cart review
 * -> confirm -> order) as an explicit state machine over {@link WhatsAppSession}, one turn per
 * inbound message. Bot copy is resolved per-turn from {@link WhatsAppMessages} against the
 * customer's chosen language (falling back to the tenant's default when the session hasn't
 * captured one yet); the vendor order-notification always stays in the tenant's default language
 * regardless of which language the customer picked.
 *
 * <p>{@code @Transactional}: {@code Product}/{@code Category.translations} are LAZY, and this is
 * the single per-turn entry point where {@code categoryRepository}/{@code productRepository}
 * results get their {@code getLocalizedName(...)} called — keeping the whole turn in one
 * persistence context avoids a {@code LazyInitializationException}. {@code tenant} itself arrives
 * already detached (loaded by the caller in its own short-lived transaction), which is why
 * {@code Tenant.supportedLanguageCodes} is EAGER instead — this annotation can't retroactively fix
 * that.
 */
@Service
public class WhatsAppConversationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConversationService.class);
    private static final int MAX_LIST_ROWS = 10;
    private static final Set<String> RESET_TRIGGERS = Set.of("hi", "hello", "hey", "menu", "start", "restart");

    private final WhatsAppSessionStore sessionStore;
    private final WhatsAppMessagingService messagingService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final WhatsAppMessages messages;

    public WhatsAppConversationService(WhatsAppSessionStore sessionStore, WhatsAppMessagingService messagingService,
                                        CategoryRepository categoryRepository, ProductRepository productRepository,
                                        CustomerRepository customerRepository, OrderService orderService,
                                        WhatsAppMessages messages) {
        this.sessionStore = sessionStore;
        this.messagingService = messagingService;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderService = orderService;
        this.messages = messages;
    }

    @Transactional
    public void handleMessage(Tenant tenant, Customer customer, String waMessageId, String textBody, String replyId) {
        String normalizedText = textBody != null ? textBody.trim().toLowerCase() : null;
        if (replyId == null && normalizedText != null && RESET_TRIGGERS.contains(normalizedText)) {
            sessionStore.save(tenant.getId(), customer.getPhoneNumber(), WhatsAppSession.initial());
            sendLanguagePrompt(tenant, customer);
            return;
        }

        WhatsAppSession session = sessionStore.get(tenant.getId(), customer.getPhoneNumber())
                .orElseGet(WhatsAppSession::initial);

        // Channels without native button/list taps (e.g. Twilio's plain-text WhatsApp API) send a
        // bare numeric reply instead of a replyId. Resolve it against the options list we last
        // sent. No-op whenever a real replyId is already present (every Meta button/list tap).
        String resolvedReplyId = replyId != null ? replyId : resolveReplyIdFromNumericText(session, normalizedText);

        switch (session.step()) {
            case LANGUAGE_SELECTION -> handleLanguageSelection(tenant, customer, session, normalizedText);
            case CATEGORY_SELECTION -> handleCategorySelection(tenant, customer, session, resolvedReplyId);
            case PRODUCT_SELECTION -> handleProductSelection(tenant, customer, session, resolvedReplyId);
            case QUANTITY_ENTRY -> handleQuantityEntry(tenant, customer, session, textBody);
            case CART_REVIEW -> handleCartReview(tenant, customer, session, resolvedReplyId);
            case CHECKOUT_CONFIRM -> handleCheckoutConfirm(tenant, customer, session, resolvedReplyId, waMessageId);
        }
    }

    private String resolveReplyIdFromNumericText(WhatsAppSession session, String normalizedText) {
        if (normalizedText == null || session.lastOptionIds() == null) {
            return null;
        }
        try {
            int index = Integer.parseInt(normalizedText);
            List<String> options = session.lastOptionIds();
            return (index >= 1 && index <= options.size()) ? options.get(index - 1) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void handleLanguageSelection(Tenant tenant, Customer customer, WhatsAppSession session, String text) {
        String languageCode = resolveLanguageChoice(tenant, text);
        if (languageCode == null) {
            sendLanguagePrompt(tenant, customer);
            return;
        }
        customer.setPreferredLanguageCode(languageCode);
        customerRepository.save(customer);

        WhatsAppSession updated = session.withLanguage(languageCode).withStep(ConversationStep.CATEGORY_SELECTION);
        sendCategoryList(tenant, customer, updated);
    }

    /** Accepts a 1-based index into the tenant's supported languages (fixed enum order), an ISO
     * code, or the language's English/native name — never the tenant's raw {@code Set} order. */
    private String resolveLanguageChoice(Tenant tenant, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> ordered = SupportedLanguage.orderedCodes(tenant.getSupportedLanguageCodes());
        try {
            int index = Integer.parseInt(text.trim());
            return (index >= 1 && index <= ordered.size()) ? ordered.get(index - 1) : null;
        } catch (NumberFormatException e) {
            return ordered.stream()
                    .filter(code -> SupportedLanguage.fromCode(code).filter(lang -> lang.matches(text)).isPresent())
                    .findFirst()
                    .orElse(null);
        }
    }

    private void handleCategorySelection(Tenant tenant, Customer customer, WhatsAppSession session, String replyId) {
        Long categoryId = parseIdFromReply(replyId, "cat:");
        Optional<Category> category = categoryId != null
                ? categoryRepository.findById(categoryId).filter(Category::isActive)
                : Optional.empty();
        if (category.isEmpty()) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.category.invalid", customerLanguage(tenant, session)));
            sendCategoryList(tenant, customer, session);
            return;
        }

        WhatsAppSession updated = session.withCategory(categoryId).withStep(ConversationStep.PRODUCT_SELECTION);
        sendProductList(tenant, customer, categoryId, updated);
    }

    private void handleProductSelection(Tenant tenant, Customer customer, WhatsAppSession session, String replyId) {
        String lang = customerLanguage(tenant, session);
        Long productId = parseIdFromReply(replyId, "prod:");
        Optional<Product> product = productId != null
                ? productRepository.findById(productId).filter(Product::isActive)
                : Optional.empty();
        if (product.isEmpty()) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.product.invalid", lang));
            sendProductList(tenant, customer, session.categoryId(), session);
            return;
        }

        WhatsAppSession updated = session.withSelectedProduct(productId).withStep(ConversationStep.QUANTITY_ENTRY);
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(), updated);
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.quantity.prompt", lang, product.get().getUnit(), product.get().getLocalizedName(lang)));
    }

    private void handleQuantityEntry(Tenant tenant, Customer customer, WhatsAppSession session, String textBody) {
        String lang = customerLanguage(tenant, session);
        BigDecimal quantity = parsePositiveDecimal(textBody);
        if (quantity == null) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.quantity.invalid", lang));
            return;
        }

        Product product = productRepository.findById(session.selectedProductId()).filter(Product::isActive)
                .orElse(null);
        if (product == null) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.product.unavailable", lang));
            restartAtCategorySelection(tenant, customer, session);
            return;
        }

        CartLine line = new CartLine(product.getId(), product.getLocalizedName(lang), product.getPrice(), quantity);
        WhatsAppSession updated = session.withCartLineAdded(line).withStep(ConversationStep.CART_REVIEW);
        sendCartSummary(tenant, customer, updated);
    }

    private void handleCartReview(Tenant tenant, Customer customer, WhatsAppSession session, String replyId) {
        if ("ADD_MORE".equals(replyId)) {
            WhatsAppSession updated = session.withStep(ConversationStep.CATEGORY_SELECTION);
            sendCategoryList(tenant, customer, updated);
            return;
        }
        if ("CHECKOUT".equals(replyId)) {
            WhatsAppSession updated = session.withStep(ConversationStep.CHECKOUT_CONFIRM);
            sendCheckoutConfirmation(tenant, customer, updated);
            return;
        }
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.cart.invalid_choice", customerLanguage(tenant, session)));
    }

    private void handleCheckoutConfirm(Tenant tenant, Customer customer, WhatsAppSession session, String replyId,
                                        String waMessageId) {
        String lang = customerLanguage(tenant, session);
        if ("CANCEL".equals(replyId)) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.checkout.cancelled", lang));
            restartAtCategorySelection(tenant, customer, session);
            return;
        }
        if (!"CONFIRM".equals(replyId)) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.checkout.invalid_choice", lang));
            return;
        }

        List<OrderItemRequest> items = session.cart().stream()
                .map(line -> new OrderItemRequest(line.productId(), line.quantity()))
                .toList();
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                customer.getId(), OrderChannel.WHATSAPP, items, null, waMessageId);

        try {
            OrderResponse order = orderService.createOrder(orderRequest);
            sessionStore.clear(tenant.getId(), customer.getPhoneNumber());

            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.order.placed", lang, order.orderNumber(),
                            String.valueOf(order.totalAmount()), order.currencyCode()));
            try {
                notifyVendor(tenant, order, customer);
            } catch (Exception e) {
                log.error("Vendor notification failed for order {} (tenant {}); customer order flow is unaffected",
                        order.orderNumber(), tenant.getId(), e);
            }
        } catch (BusinessRuleViolationException | ResourceNotFoundException e) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.order.failed", lang, e.getMessage()));
            restartAtCategorySelection(tenant, customer, session);
        }
    }

    /** Always the tenant's default (vendor's) language — never the customer's chosen one. */
    private void notifyVendor(Tenant tenant, OrderResponse order, Customer customer) {
        String vendorPhone = tenant.getVendorNotificationPhoneNumber();
        if (vendorPhone == null || vendorPhone.isBlank()) {
            log.info("No vendor notification phone number configured for tenant {}; skipping vendor alert",
                    tenant.getId());
            return;
        }
        String customerLabel = customer.getFullName() != null ? customer.getFullName() : customer.getPhoneNumber();
        messagingService.sendText(tenant, null, vendorPhone,
                messages.get("bot.vendor.new_order", tenant.getDefaultLanguageCode(),
                        order.orderNumber(), customerLabel, customer.getPhoneNumber(),
                        String.valueOf(order.totalAmount()), order.currencyCode()));
    }

    private void restartAtCategorySelection(Tenant tenant, Customer customer, WhatsAppSession session) {
        WhatsAppSession reset = session.withStep(ConversationStep.CATEGORY_SELECTION).withEmptyCart();
        sendCategoryList(tenant, customer, reset);
    }

    private void sendLanguagePrompt(Tenant tenant, Customer customer) {
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.languagePrompt(tenant.getSupportedLanguageCodes()));
    }

    /** Falls back to the tenant's default language when the session hasn't captured one yet
     * (e.g. mid-flow states constructed directly in tests, bypassing LANGUAGE_SELECTION). */
    private String customerLanguage(Tenant tenant, WhatsAppSession session) {
        return session.languageCode() != null ? session.languageCode() : tenant.getDefaultLanguageCode();
    }

    /**
     * Every send* method below is the sole place that persists {@code session} for its step,
     * folding in {@code lastOptionIds} (the ordered ids behind the numbers/rows just shown) in the
     * same save — callers must not also save before invoking these, or the session would be
     * written twice per turn.
     */
    private void sendCategoryList(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        List<Category> categories = categoryRepository.findByActiveTrue(PageRequest.of(0, MAX_LIST_ROWS)).getContent();
        if (categories.isEmpty()) {
            sessionStore.save(tenant.getId(), customer.getPhoneNumber(), session.withLastOptionIds(List.of()));
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.category.empty", lang));
            return;
        }
        List<ListRow> rows = categories.stream()
                .map(c -> new ListRow("cat:" + c.getId(), truncate(c.getLocalizedName(lang), 24),
                        truncate(c.getLocalizedDescription(lang), 72)))
                .toList();
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(rows.stream().map(ListRow::id).toList()));
        messagingService.sendInteractiveList(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.category.prompt", lang), messages.get("bot.category.button", lang),
                List.of(new ListSection(messages.get("bot.category.section_title", lang), rows)), lang);
    }

    private void sendProductList(Tenant tenant, Customer customer, Long categoryId, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        List<Product> products = productRepository.findByCategoryId(categoryId, PageRequest.of(0, MAX_LIST_ROWS))
                .getContent().stream().filter(Product::isActive).toList();
        if (products.isEmpty()) {
            sessionStore.save(tenant.getId(), customer.getPhoneNumber(), session.withLastOptionIds(List.of()));
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.product.empty", lang));
            return;
        }
        List<ListRow> rows = products.stream()
                .map(p -> new ListRow("prod:" + p.getId(), truncate(p.getLocalizedName(lang), 24),
                        truncate(p.getPrice() + " " + p.getCurrencyCode() + " / " + p.getUnit(), 72)))
                .toList();
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(rows.stream().map(ListRow::id).toList()));
        messagingService.sendInteractiveList(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.product.prompt", lang), messages.get("bot.product.button", lang),
                List.of(new ListSection(messages.get("bot.product.section_title", lang), rows)), lang);
    }

    private void sendCartSummary(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        StringBuilder text = new StringBuilder(messages.get("bot.cart.header", lang));
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : session.cart()) {
            BigDecimal lineTotal = line.unitPrice().multiply(line.quantity());
            total = total.add(lineTotal);
            text.append(messages.get("bot.cart.line", lang, line.productName(), String.valueOf(line.quantity()),
                    String.valueOf(lineTotal)));
        }
        text.append(messages.get("bot.cart.total", lang, String.valueOf(total)));

        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(List.of("ADD_MORE", "CHECKOUT")));
        messagingService.sendInteractiveButtons(tenant, customer, customer.getPhoneNumber(), text.toString(),
                List.of(ReplyButton.of("ADD_MORE", messages.get("bot.cart.add_more_button", lang)),
                        ReplyButton.of("CHECKOUT", messages.get("bot.cart.checkout_button", lang))), lang);
    }

    private void sendCheckoutConfirmation(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        BigDecimal total = session.cart().stream()
                .map(line -> line.unitPrice().multiply(line.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(List.of("CONFIRM", "CANCEL")));
        messagingService.sendInteractiveButtons(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.checkout.confirm_prompt", lang, String.valueOf(total), tenant.getCurrencyCode()),
                List.of(ReplyButton.of("CONFIRM", messages.get("bot.checkout.confirm_button", lang)),
                        ReplyButton.of("CANCEL", messages.get("bot.checkout.cancel_button", lang))), lang);
    }

    private Long parseIdFromReply(String replyId, String prefix) {
        if (replyId == null || !replyId.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.valueOf(replyId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parsePositiveDecimal(String text) {
        if (text == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
