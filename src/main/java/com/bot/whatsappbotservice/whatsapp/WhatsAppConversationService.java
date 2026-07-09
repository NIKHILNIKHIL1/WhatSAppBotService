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
import com.bot.whatsappbotservice.inventory.Inventory;
import com.bot.whatsappbotservice.inventory.InventoryRepository;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemResponse;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.tenant.MessagingProvider;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListRow;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListSection;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ReplyButton;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
    // Meta's interactive list caps at 10 rows total across all sections — the category page size
    // is trimmed to leave room for the two reserved "My Orders"/"Help" rows appended below it.
    private static final int MAX_CATEGORY_ROWS = MAX_LIST_ROWS - 2;
    private static final Set<String> RESET_TRIGGERS = Set.of("hi", "hello", "hey", "menu", "start", "restart");
    private static final Set<String> HELP_TRIGGERS = Set.of("help", "support");
    private static final Set<String> ORDER_HISTORY_TRIGGERS =
            Set.of("orders", "my orders", "myorders", "order history");
    private static final String MENU_ORDERS_ID = "menu:orders";
    private static final String MENU_HELP_ID = "menu:help";
    private static final int RECENT_ORDERS_LIMIT = 3;
    private static final String HISTORY_WEEK_ID = "HISTORY_WEEK";
    private static final String HISTORY_MONTH_ID = "HISTORY_MONTH";
    private static final String HISTORY_YEAR_ID = "HISTORY_YEAR";
    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneOffset.UTC);

    private final WhatsAppSessionStore sessionStore;
    private final WhatsAppMessagingService messagingService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final OrderHistoryPdfGenerator orderHistoryPdfGenerator;
    private final WhatsAppMessages messages;

    public WhatsAppConversationService(WhatsAppSessionStore sessionStore, WhatsAppMessagingService messagingService,
                                        CategoryRepository categoryRepository, ProductRepository productRepository,
                                        InventoryRepository inventoryRepository, CustomerRepository customerRepository,
                                        OrderService orderService, OrderHistoryPdfGenerator orderHistoryPdfGenerator,
                                        WhatsAppMessages messages) {
        this.sessionStore = sessionStore;
        this.messagingService = messagingService;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.customerRepository = customerRepository;
        this.orderService = orderService;
        this.orderHistoryPdfGenerator = orderHistoryPdfGenerator;
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

        // Help/order-history are global escape hatches, reachable from any step — mirroring how
        // RESET_TRIGGERS above interrupts any step. Help never touches session state at all, so
        // whatever prompt/buttons were already on-screen (e.g. mid QUANTITY_ENTRY) are still valid
        // to reply to afterward; order history does move the session to its own step, so — like
        // every other mid-flow detour in this class — the customer lands back on the category list
        // rather than resuming whatever they were doing, with the cart left untouched.
        if (MENU_HELP_ID.equals(resolvedReplyId)
                || (replyId == null && normalizedText != null && HELP_TRIGGERS.contains(normalizedText))) {
            sendHelp(tenant, customer, session);
            return;
        }
        if (MENU_ORDERS_ID.equals(resolvedReplyId)
                || (replyId == null && normalizedText != null && ORDER_HISTORY_TRIGGERS.contains(normalizedText))) {
            showOrderHistoryMenu(tenant, customer, session);
            return;
        }

        // Another global escape hatch: free text that isn't a recognized reply/keyword and matches
        // an active product's SKU lets the customer skip straight to quantity entry, bypassing
        // category/product browsing entirely. Not offered before LANGUAGE_SELECTION completes (bot
        // copy needs a language first) or mid QUANTITY_ENTRY (free text there is already a quantity
        // reply for the product already selected).
        if (resolvedReplyId == null && normalizedText != null && !normalizedText.isBlank()
                && session.step() != ConversationStep.LANGUAGE_SELECTION
                && session.step() != ConversationStep.QUANTITY_ENTRY) {
            Optional<Product> productByCode = productRepository.findBySkuIgnoreCase(normalizedText)
                    .filter(Product::isActive);
            if (productByCode.isPresent()) {
                handleProductCodeEntry(tenant, customer, session, productByCode.get());
                return;
            }
        }

        switch (session.step()) {
            case LANGUAGE_SELECTION -> handleLanguageSelection(tenant, customer, session, normalizedText);
            case CATEGORY_SELECTION -> handleCategorySelection(tenant, customer, session, resolvedReplyId);
            case PRODUCT_SELECTION -> handleProductSelection(tenant, customer, session, resolvedReplyId);
            case QUANTITY_ENTRY -> handleQuantityEntry(tenant, customer, session, textBody);
            case CART_REVIEW -> handleCartReview(tenant, customer, session, resolvedReplyId);
            case CART_REMOVE_SELECTION -> handleCartRemoveSelection(tenant, customer, session, resolvedReplyId);
            case ORDER_HISTORY_MENU -> handleOrderHistoryMenu(tenant, customer, session, resolvedReplyId);
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
            sendCartReminder(tenant, customer, session);
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
            sendCartReminder(tenant, customer, session);
            sendProductList(tenant, customer, session.categoryId(), session);
            return;
        }
        if (isOutOfStock(productId)) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.product.selected_out_of_stock", lang, product.get().getLocalizedName(lang)));
            sendCartReminder(tenant, customer, session);
            sendProductList(tenant, customer, session.categoryId(), session);
            return;
        }

        WhatsAppSession updated = session.withSelectedProduct(productId).withStep(ConversationStep.QUANTITY_ENTRY);
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(), updated);
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.quantity.prompt", lang, product.get().getUnit(), product.get().getLocalizedName(lang)));
    }

    /** Same destination as {@link #handleProductSelection}'s success path (straight to quantity
     * entry) — just reached via a typed SKU instead of a category/product list tap. */
    private void handleProductCodeEntry(Tenant tenant, Customer customer, WhatsAppSession session, Product product) {
        String lang = customerLanguage(tenant, session);
        if (isOutOfStock(product.getId())) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.product.selected_out_of_stock", lang, product.getLocalizedName(lang)));
            sendCartReminder(tenant, customer, session);
            sendCategoryList(tenant, customer, session.withStep(ConversationStep.CATEGORY_SELECTION));
            return;
        }

        WhatsAppSession updated =
                session.withSelectedProduct(product.getId()).withStep(ConversationStep.QUANTITY_ENTRY);
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(), updated);
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.quantity.prompt", lang, product.getUnit(), product.getLocalizedName(lang)));
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
        if ("REMOVE".equals(replyId)) {
            WhatsAppSession updated = session.withStep(ConversationStep.CART_REMOVE_SELECTION);
            sendCartRemovalList(tenant, customer, updated);
            return;
        }
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.cart.invalid_choice", customerLanguage(tenant, session)));
    }

    private static final String CART_REMOVE_BACK_ID = "cart:back";

    private void handleCartRemoveSelection(Tenant tenant, Customer customer, WhatsAppSession session,
                                            String replyId) {
        String lang = customerLanguage(tenant, session);
        if (CART_REMOVE_BACK_ID.equals(replyId)) {
            sendCartSummary(tenant, customer, session.withStep(ConversationStep.CART_REVIEW));
            return;
        }
        Integer index = parseIndexFromReply(replyId, "rmv:");
        if (index == null || index < 0 || index >= session.cart().size()) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.cart.invalid_removal", lang));
            sendCartRemovalList(tenant, customer, session);
            return;
        }

        CartLine removedLine = session.cart().get(index);
        WhatsAppSession updated = session.withCartLineRemoved(index).withStep(ConversationStep.CART_REVIEW);
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.cart.removed", lang, removedLine.productName()));

        if (updated.cart().isEmpty()) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.cart.empty_after_removal", lang));
            sendCategoryList(tenant, customer, updated.withStep(ConversationStep.CATEGORY_SELECTION));
            return;
        }
        sendCartSummary(tenant, customer, updated);
    }

    /** Stateless by design — no session mutation, no step change — so whatever the customer was
     * doing before asking for help is exactly where they left off afterward. */
    private void sendHelp(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        String vendorPhone = tenant.getVendorNotificationPhoneNumber();
        String contactLine = (vendorPhone != null && !vendorPhone.isBlank())
                ? messages.get("bot.help.contact_line", lang, vendorPhone)
                : messages.get("bot.help.no_contact_line", lang);
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.help.message", lang, contactLine));
    }

    private void showOrderHistoryMenu(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        List<OrderResponse> recent = orderService.listRecentForCustomer(customer.getId(), RECENT_ORDERS_LIMIT);
        if (recent.isEmpty()) {
            // Nothing to show and nothing to export yet — leave the session untouched rather than
            // offering Week/Month/Year buttons over an empty history.
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(), messages.get("bot.orders.none", lang));
            return;
        }
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(), renderRecentOrders(recent, lang));

        WhatsAppSession updated = session.withStep(ConversationStep.ORDER_HISTORY_MENU)
                .withLastOptionIds(List.of(HISTORY_WEEK_ID, HISTORY_MONTH_ID, HISTORY_YEAR_ID));
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(), updated);
        messagingService.sendInteractiveButtons(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.orders.history_prompt", lang),
                List.of(ReplyButton.of(HISTORY_WEEK_ID, messages.get("bot.orders.week_button", lang)),
                        ReplyButton.of(HISTORY_MONTH_ID, messages.get("bot.orders.month_button", lang)),
                        ReplyButton.of(HISTORY_YEAR_ID, messages.get("bot.orders.year_button", lang))), lang);
    }

    private String renderRecentOrders(List<OrderResponse> orders, String lang) {
        StringBuilder text = new StringBuilder(messages.get("bot.orders.header", lang, String.valueOf(orders.size())));
        for (OrderResponse order : orders) {
            text.append(messages.get("bot.orders.line", lang, order.orderNumber(),
                    ORDER_DATE_FORMAT.format(order.createdAt()), order.status().name(), order.currencyCode(),
                    String.valueOf(order.totalAmount())));
        }
        return text.toString();
    }

    private void handleOrderHistoryMenu(Tenant tenant, Customer customer, WhatsAppSession session, String replyId) {
        String lang = customerLanguage(tenant, session);
        int windowDays;
        String periodKey;
        if (HISTORY_WEEK_ID.equals(replyId)) {
            windowDays = 7;
            periodKey = "bot.orders.period_week";
        } else if (HISTORY_MONTH_ID.equals(replyId)) {
            windowDays = 30;
            periodKey = "bot.orders.period_month";
        } else if (HISTORY_YEAR_ID.equals(replyId)) {
            windowDays = 365;
            periodKey = "bot.orders.period_year";
        } else {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.orders.invalid_choice", lang));
            return;
        }

        if (tenant.getMessagingProvider() == MessagingProvider.TWILIO) {
            // Twilio's WhatsApp API can only send a document via a public HTTPS link, which this
            // app doesn't expose — see WhatsAppMessagingService.sendDocument's Meta-only note.
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.orders.pdf_unsupported_channel", lang));
        } else {
            String periodLabel = messages.get(periodKey, lang);
            Instant to = Instant.now();
            List<OrderResponse> orders =
                    orderService.listForCustomerBetween(customer.getId(), to.minus(Duration.ofDays(windowDays)), to);
            if (orders.isEmpty()) {
                messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                        messages.get("bot.orders.pdf_empty", lang));
            } else {
                byte[] pdf = orderHistoryPdfGenerator.generate(tenant, customer, orders, periodLabel, lang);
                messagingService.sendDocument(tenant, customer, customer.getPhoneNumber(), pdf, "order-history.pdf",
                        messages.get("bot.orders.pdf_caption", lang, periodLabel));
            }
        }

        sendCartReminder(tenant, customer, session);
        sendCategoryList(tenant, customer, session.withStep(ConversationStep.CATEGORY_SELECTION));
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

            messagingService.sendText(tenant, customer, customer.getPhoneNumber(), renderOrderReceipt(order, lang));
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
        List<Category> categories =
                categoryRepository.findByActiveTrue(PageRequest.of(0, MAX_CATEGORY_ROWS)).getContent();
        if (categories.isEmpty()) {
            sessionStore.save(tenant.getId(), customer.getPhoneNumber(), session.withLastOptionIds(List.of()));
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.category.empty", lang));
            return;
        }
        // A category reads as out of stock only once every active product under it is out of
        // stock — an empty category (no active products at all) is not "out of stock", it's just
        // empty, so both sets below are needed to tell the two cases apart.
        List<Long> categoryIds = categories.stream().map(Category::getId).toList();
        Set<Long> categoriesWithActiveProduct = productRepository.findCategoryIdsWithActiveProduct(categoryIds);
        Set<Long> categoriesWithStock = inventoryRepository.findCategoryIdsWithInStockProduct(categoryIds);
        List<ListRow> rows = categories.stream()
                .map(c -> {
                    boolean outOfStock = categoriesWithActiveProduct.contains(c.getId())
                            && !categoriesWithStock.contains(c.getId());
                    String description = outOfStock
                            ? messages.get("bot.category.out_of_stock", lang)
                            : c.getLocalizedDescription(lang);
                    return new ListRow("cat:" + c.getId(), truncate(c.getLocalizedName(lang), 24),
                            truncate(description, 72));
                })
                .toList();
        // Two reserved rows, always available from this "home screen" regardless of catalog
        // contents — the same destinations the HELP_TRIGGERS/ORDER_HISTORY_TRIGGERS keywords reach.
        List<ListRow> menuRows = List.of(
                new ListRow(MENU_ORDERS_ID, messages.get("bot.menu.orders_row_title", lang),
                        messages.get("bot.menu.orders_row_description", lang)),
                new ListRow(MENU_HELP_ID, messages.get("bot.menu.help_row_title", lang),
                        messages.get("bot.menu.help_row_description", lang)));
        List<String> allOptionIds = Stream.concat(rows.stream().map(ListRow::id), menuRows.stream().map(ListRow::id))
                .toList();
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(), session.withLastOptionIds(allOptionIds));
        messagingService.sendInteractiveList(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.category.prompt", lang), messages.get("bot.category.button", lang),
                List.of(new ListSection(messages.get("bot.category.section_title", lang), rows),
                        new ListSection(messages.get("bot.menu.section_title", lang), menuRows)), lang);
    }

    private void sendProductList(Tenant tenant, Customer customer, Long categoryId, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        List<Product> products = productRepository.findByCategoryId(categoryId, PageRequest.of(0, MAX_LIST_ROWS))
                .getContent().stream().filter(Product::isActive).toList();
        if (products.isEmpty()) {
            messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                    messages.get("bot.product.empty", lang));
            // Nothing to select in this category — hand the customer straight back to the category
            // list (cart untouched) instead of parking them on an empty PRODUCT_SELECTION step,
            // where every following reply (numeric fallback or free text) used to bounce forever
            // between "Please select a product from the list." and this same empty-category message.
            sendCartReminder(tenant, customer, session);
            sendCategoryList(tenant, customer, session.withStep(ConversationStep.CATEGORY_SELECTION));
            return;
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, BigDecimal> quantityByProductId = inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Inventory::getQuantityOnHand));
        List<ListRow> rows = products.stream()
                .map(p -> {
                    BigDecimal quantity = quantityByProductId.get(p.getId());
                    boolean outOfStock = quantity == null || quantity.signum() <= 0;
                    String description = outOfStock
                            ? messages.get("bot.product.out_of_stock", lang)
                            : p.getPrice() + " " + p.getCurrencyCode() + " / " + p.getUnit();
                    return new ListRow("prod:" + p.getId(), truncate(p.getLocalizedName(lang), 24),
                            truncate(description, 72));
                })
                .toList();
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(rows.stream().map(ListRow::id).toList()));
        messagingService.sendInteractiveList(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.product.prompt", lang), messages.get("bot.product.button", lang),
                List.of(new ListSection(messages.get("bot.product.section_title", lang), rows)), lang);
    }

    private boolean isOutOfStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(i -> i.getQuantityOnHand().signum() <= 0)
                .orElse(true);
    }

    private void sendCartSummary(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        String text = renderCartLines("bot.cart.header", session, lang);

        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(List.of("ADD_MORE", "REMOVE", "CHECKOUT")));
        messagingService.sendInteractiveButtons(tenant, customer, customer.getPhoneNumber(), text,
                List.of(ReplyButton.of("ADD_MORE", messages.get("bot.cart.add_more_button", lang)),
                        ReplyButton.of("REMOVE", messages.get("bot.cart.remove_button", lang)),
                        ReplyButton.of("CHECKOUT", messages.get("bot.cart.checkout_button", lang))), lang);
    }

    /**
     * Lets the customer know what's already in their cart before re-showing the category/product
     * list — otherwise a dead end (out-of-stock pick, empty category) bounces them back to browsing
     * with no reminder of what they'd already added, which reads as if it was lost. A no-op while
     * the cart is empty, which keeps every browsing entry point before the first add-to-cart
     * unchanged.
     */
    private void sendCartReminder(Tenant tenant, Customer customer, WhatsAppSession session) {
        if (session.cart().isEmpty()) {
            return;
        }
        String lang = customerLanguage(tenant, session);
        messagingService.sendText(tenant, customer, customer.getPhoneNumber(),
                renderCartLines("bot.cart.reminder_header", session, lang));
    }

    private String renderCartLines(String headerKey, WhatsAppSession session, String lang) {
        return renderCartLines(headerKey, session.cart(), null, lang);
    }

    /** {@code currencyCode == null} renders the plain "{@code Total: 165.00}" line used for
     * in-progress browsing (cart review/reminder); a non-null currency renders
     * "{@code Total: 165.00 INR}", used once the customer is looking at a total they're about to
     * commit to (checkout confirmation, final order receipt). */
    private String renderCartLines(String headerKey, List<CartLine> lines, String currencyCode, String lang) {
        StringBuilder text = new StringBuilder(messages.get(headerKey, lang));
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : lines) {
            BigDecimal lineTotal = line.unitPrice().multiply(line.quantity());
            total = total.add(lineTotal);
            text.append(messages.get("bot.cart.line", lang, line.productName(), String.valueOf(line.quantity()),
                    String.valueOf(lineTotal)));
        }
        text.append(currencyCode != null
                ? messages.get("bot.cart.total_with_currency", lang, String.valueOf(total), currencyCode)
                : messages.get("bot.cart.total", lang, String.valueOf(total)));
        return text.toString();
    }

    /** Same line-item/total formatting as {@link #renderCartLines}, but reads from the persisted
     * {@link OrderItemResponse} snapshots returned by {@code orderService.createOrder} rather than
     * the in-flight session cart — the order is already committed by the time this is sent. */
    private String renderOrderReceipt(OrderResponse order, String lang) {
        StringBuilder text = new StringBuilder(messages.get("bot.order.placed", lang, order.orderNumber()));
        for (OrderItemResponse item : order.items()) {
            text.append(messages.get("bot.cart.line", lang, item.productNameSnapshot(),
                    String.valueOf(item.quantity()), String.valueOf(item.lineTotal())));
        }
        text.append(messages.get("bot.cart.total_with_currency", lang, String.valueOf(order.totalAmount()),
                order.currencyCode()));
        return text.toString();
    }

    private void sendCartRemovalList(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        List<CartLine> cart = session.cart();
        List<ListRow> rows = IntStream.range(0, cart.size())
                .mapToObj(i -> {
                    CartLine line = cart.get(i);
                    BigDecimal lineTotal = line.unitPrice().multiply(line.quantity());
                    String description = line.quantity() + " x " + line.unitPrice() + " = " + lineTotal;
                    return new ListRow("rmv:" + i, truncate(line.productName(), 24), truncate(description, 72));
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        rows.add(new ListRow(CART_REMOVE_BACK_ID, messages.get("bot.cart.remove_back_row_title", lang),
                messages.get("bot.cart.remove_back_row_description", lang)));

        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(rows.stream().map(ListRow::id).toList()));
        messagingService.sendInteractiveList(tenant, customer, customer.getPhoneNumber(),
                messages.get("bot.cart.remove_prompt", lang), messages.get("bot.cart.remove_button", lang),
                List.of(new ListSection(messages.get("bot.cart.remove_section_title", lang), rows)), lang);
    }

    private void sendCheckoutConfirmation(Tenant tenant, Customer customer, WhatsAppSession session) {
        String lang = customerLanguage(tenant, session);
        String summary = renderCartLines("bot.checkout.summary_header", session.cart(), tenant.getCurrencyCode(), lang)
                + messages.get("bot.checkout.confirm_question", lang);
        sessionStore.save(tenant.getId(), customer.getPhoneNumber(),
                session.withLastOptionIds(List.of("CONFIRM", "CANCEL")));
        messagingService.sendInteractiveButtons(tenant, customer, customer.getPhoneNumber(), summary,
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

    private Integer parseIndexFromReply(String replyId, String prefix) {
        if (replyId == null || !replyId.startsWith(prefix)) {
            return null;
        }
        try {
            return Integer.valueOf(replyId.substring(prefix.length()));
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
