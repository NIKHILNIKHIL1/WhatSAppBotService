package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.Category;
import com.bot.whatsappbotservice.catalog.CategoryRepository;
import com.bot.whatsappbotservice.catalog.Product;
import com.bot.whatsappbotservice.catalog.ProductRepository;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.inventory.Inventory;
import com.bot.whatsappbotservice.inventory.InventoryRepository;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.tenant.MessagingProvider;
import com.bot.whatsappbotservice.tenant.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;

class WhatsAppConversationServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String PHONE = "+14155550100";

    @Mock
    private WhatsAppSessionStore sessionStore;
    @Mock
    private WhatsAppMessagingService messagingService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderService orderService;

    private WhatsAppConversationService conversationService;
    private Tenant tenant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        WhatsAppMessages messages = new WhatsAppMessages(messageSource);
        OrderHistoryPdfGenerator orderHistoryPdfGenerator = new OrderHistoryPdfGenerator(messages);

        conversationService = new WhatsAppConversationService(
                sessionStore, messagingService, categoryRepository, productRepository, inventoryRepository,
                customerRepository, orderService, orderHistoryPdfGenerator, messages);

        tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setCurrencyCode("INR");
        tenant.setSupportedLanguageCodes(Set.of("en", "hi"));

        customer = new Customer();
        customer.setId(9L);
        customer.setPhoneNumber(PHONE);
    }

    @Test
    void resetTriggerAlwaysRestartsAtLanguageSelection() {
        conversationService.handleMessage(tenant, customer, "wamid-0", "hi", null);

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.LANGUAGE_SELECTION);
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), anyString());
    }

    @Test
    void languageSelectionAdvancesToCategorySelectionAndListsCategories() {
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(WhatsAppSession.initial()));
        Category dairy = new Category();
        dairy.setId(5L);
        dairy.setName("Dairy");
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of(dairy)));

        conversationService.handleMessage(tenant, customer, "wamid-1", "1", null);

        verify(customerRepository).save(customer);
        assertThat(customer.getPreferredLanguageCode()).isEqualTo("en");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CATEGORY_SELECTION);
        verify(messagingService).sendInteractiveList(eq(tenant), eq(customer), eq(PHONE), anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void unrecognizedLanguageReplyRepromptsWithoutAdvancing() {
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(WhatsAppSession.initial()));

        conversationService.handleMessage(tenant, customer, "wamid-1b", "bonjour", null);

        verify(sessionStore, never()).save(any(), any(), any());
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), anyString());
    }

    @Test
    void categorySelectionAdvancesToProductSelectionAndListsProducts() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Category dairy = new Category();
        dairy.setId(5L);
        dairy.setActive(true);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(dairy));
        Product milk = new Product();
        milk.setId(10L);
        milk.setActive(true);
        milk.setName("Milk");
        milk.setUnit("ltr");
        milk.setPrice(new BigDecimal("55.00"));
        milk.setCurrencyCode("INR");
        when(productRepository.findByCategoryId(eq(5L), any())).thenReturn(new PageImpl<>(List.of(milk)));

        conversationService.handleMessage(tenant, customer, "wamid-2", null, "cat:5");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.PRODUCT_SELECTION);
        assertThat(captor.getValue().categoryId()).isEqualTo(5L);
        verify(messagingService).sendInteractiveList(eq(tenant), eq(customer), eq(PHONE), anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void emptyProductListSendsCustomerBackToCategorySelectionWithCartPreserved() {
        CartLine existingLine = new CartLine(10L, "Cream Milk", new BigDecimal("150.00"), BigDecimal.valueOf(12));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION)
                .withCartLineAdded(existingLine);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Category butterMilk = new Category();
        butterMilk.setId(6L);
        butterMilk.setActive(true);
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(butterMilk));
        when(productRepository.findByCategoryId(eq(6L), any())).thenReturn(new PageImpl<>(List.of()));
        Category dairy = new Category();
        dairy.setId(5L);
        dairy.setName("Dairy");
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of(dairy)));

        conversationService.handleMessage(tenant, customer, "wamid-2b", null, "cat:6");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        // The dead-end this guards against: previously the session stayed on PRODUCT_SELECTION
        // with an empty option list, so every later reply bounced forever between "Please select a
        // product from the list." and "No products available in this category yet." — recovering
        // to CATEGORY_SELECTION (with the cart intact) is what breaks that loop.
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CATEGORY_SELECTION);
        assertThat(captor.getValue().cart()).containsExactly(existingLine);
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("No products available in this category yet."));
        verify(messagingService).sendInteractiveList(eq(tenant), eq(customer), eq(PHONE), anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void selectingOutOfStockProductRepromptsProductListInsteadOfAdvancing() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.PRODUCT_SELECTION).withCategory(5L);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product creamMilk = new Product();
        creamMilk.setId(11L);
        creamMilk.setActive(true);
        creamMilk.setName("Cream Milk");
        when(productRepository.findById(11L)).thenReturn(Optional.of(creamMilk));
        Inventory inventory = new Inventory();
        inventory.setQuantityOnHand(BigDecimal.ZERO);
        when(inventoryRepository.findByProductId(11L)).thenReturn(Optional.of(inventory));
        when(productRepository.findByCategoryId(eq(5L), any())).thenReturn(new PageImpl<>(List.of(creamMilk)));

        conversationService.handleMessage(tenant, customer, "wamid-3b", null, "prod:11");

        // sendProductList re-listing the (still out-of-stock) product does persist a session — the
        // point being verified here is that it stays on PRODUCT_SELECTION rather than advancing.
        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.PRODUCT_SELECTION);
        assertThat(captor.getValue().selectedProductId()).isNull();
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Sorry, Cream Milk just went out of stock. Please choose another product."));
        verify(messagingService).sendInteractiveList(eq(tenant), eq(customer), eq(PHONE), anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void selectingOutOfStockProductWithExistingCartRemindsCustomerWhatsAlreadyInCart() {
        CartLine existingLine = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(2));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.PRODUCT_SELECTION)
                .withCategory(5L).withCartLineAdded(existingLine);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product creamMilk = new Product();
        creamMilk.setId(11L);
        creamMilk.setActive(true);
        creamMilk.setName("Cream Milk");
        when(productRepository.findById(11L)).thenReturn(Optional.of(creamMilk));
        Inventory inventory = new Inventory();
        inventory.setQuantityOnHand(BigDecimal.ZERO);
        when(inventoryRepository.findByProductId(11L)).thenReturn(Optional.of(inventory));
        when(productRepository.findByCategoryId(eq(5L), any())).thenReturn(new PageImpl<>(List.of(creamMilk)));

        conversationService.handleMessage(tenant, customer, "wamid-3c", null, "prod:11");

        // The dead-end this guards against: a customer who already added an item, then hits an
        // out-of-stock pick, previously only ever saw "please choose another product" with no
        // reminder of what they'd already added — reading as if the earlier item had been lost.
        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().cart()).containsExactly(existingLine);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingService, times(2)).sendText(eq(tenant), eq(customer), eq(PHONE), textCaptor.capture());
        assertThat(textCaptor.getAllValues().get(0))
                .isEqualTo("Sorry, Cream Milk just went out of stock. Please choose another product.");
        assertThat(textCaptor.getAllValues().get(1)).contains("Milk").contains("2").contains("110.00");
    }

    @Test
    void productSelectionAdvancesToQuantityEntry() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.PRODUCT_SELECTION).withCategory(5L);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product milk = new Product();
        milk.setId(10L);
        milk.setActive(true);
        milk.setName("Milk");
        milk.setUnit("ltr");
        milk.setPrice(new BigDecimal("55.00"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(milk));
        Inventory inventory = new Inventory();
        inventory.setQuantityOnHand(new BigDecimal("20"));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        conversationService.handleMessage(tenant, customer, "wamid-3", null, "prod:10");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.QUANTITY_ENTRY);
        assertThat(captor.getValue().selectedProductId()).isEqualTo(10L);
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), anyString());
    }

    @Test
    void quantityEntryAddsCartLineAndShowsSummary() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.QUANTITY_ENTRY)
                .withCategory(5L).withSelectedProduct(10L);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product milk = new Product();
        milk.setId(10L);
        milk.setActive(true);
        milk.setName("Milk");
        milk.setUnit("ltr");
        milk.setPrice(new BigDecimal("55.00"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(milk));

        conversationService.handleMessage(tenant, customer, "wamid-4", "3", null);

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CART_REVIEW);
        assertThat(captor.getValue().cart()).hasSize(1);
        assertThat(captor.getValue().cart().get(0).quantity()).isEqualByComparingTo("3");
        verify(messagingService).sendInteractiveButtons(eq(tenant), eq(customer), eq(PHONE), anyString(), anyList(), anyString());
    }

    @Test
    void invalidQuantityRepromptsWithoutAdvancing() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.QUANTITY_ENTRY).withSelectedProduct(10L);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-4b", "not-a-number", null);

        verify(sessionStore, never()).save(any(), any(), any());
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), anyString());
    }

    @Test
    void cartReviewCheckoutAdvancesToConfirmation() {
        CartLine line = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CART_REVIEW).withCartLineAdded(line);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-5", null, "CHECKOUT");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CHECKOUT_CONFIRM);
        verify(messagingService).sendInteractiveButtons(eq(tenant), eq(customer), eq(PHONE), anyString(), anyList(), anyString());
    }

    @Test
    void checkoutConfirmationShowsItemizedSummaryAndTotalWithCurrency() {
        CartLine milk = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        CartLine bread = new CartLine(20L, "Bread", new BigDecimal("40.00"), BigDecimal.valueOf(1));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CART_REVIEW)
                .withCartLineAdded(milk).withCartLineAdded(bread);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-checkout-1", null, "CHECKOUT");

        // Unlike the plain cart-review total, the checkout confirmation carries the tenant's
        // currency code alongside the total — the customer is about to commit to this amount.
        verify(messagingService).sendInteractiveButtons(eq(tenant), eq(customer), eq(PHONE),
                eq("Order Summary:\n- Milk x 3 = 165.00\n- Bread x 1 = 40.00\n\nTotal: 205.00 INR\n"
                        + "Confirm your order?"),
                anyList(), anyString());
    }

    @Test
    void cartReviewRemoveShowsRemovalListOfCurrentCartItems() {
        CartLine milk = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        CartLine bread = new CartLine(20L, "Bread", new BigDecimal("40.00"), BigDecimal.valueOf(1));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CART_REVIEW)
                .withCartLineAdded(milk).withCartLineAdded(bread);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-5b", null, "REMOVE");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CART_REMOVE_SELECTION);
        // rmv:0 / rmv:1 address the two cart lines by index; the trailing id lets the customer
        // back out of removal without touching the cart.
        assertThat(captor.getValue().lastOptionIds()).containsExactly("rmv:0", "rmv:1", "cart:back");
        verify(messagingService).sendInteractiveList(eq(tenant), eq(customer), eq(PHONE), anyString(), anyString(),
                anyList(), anyString());
    }

    @Test
    void removingCartLineDeletesItAndReturnsToCartSummary() {
        CartLine milk = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        CartLine bread = new CartLine(20L, "Bread", new BigDecimal("40.00"), BigDecimal.valueOf(1));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CART_REMOVE_SELECTION)
                .withCartLineAdded(milk).withCartLineAdded(bread);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-5c", null, "rmv:0");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CART_REVIEW);
        assertThat(captor.getValue().cart()).containsExactly(bread);
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), eq("Removed Milk from your cart."));
        verify(messagingService).sendInteractiveButtons(eq(tenant), eq(customer), eq(PHONE), anyString(), anyList(),
                anyString());
    }

    @Test
    void removingLastCartLineSendsBackToCategorySelectionWithEmptyCart() {
        CartLine milk = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CART_REMOVE_SELECTION)
                .withCartLineAdded(milk);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of()));

        conversationService.handleMessage(tenant, customer, "wamid-5d", null, "rmv:0");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CATEGORY_SELECTION);
        assertThat(captor.getValue().cart()).isEmpty();
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), eq("Removed Milk from your cart."));
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Your cart is now empty. Let's find something for you!"));
    }

    @Test
    void cartRemoveBackReturnsToCartSummaryWithoutChangingCart() {
        CartLine milk = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CART_REMOVE_SELECTION)
                .withCartLineAdded(milk);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-5e", null, "cart:back");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CART_REVIEW);
        assertThat(captor.getValue().cart()).containsExactly(milk);
        verify(messagingService, never()).sendText(any(), any(), any(), any());
        verify(messagingService).sendInteractiveButtons(eq(tenant), eq(customer), eq(PHONE), anyString(), anyList(),
                anyString());
    }

    @Test
    void confirmingOrderCreatesOrderNotifiesCustomerAndVendorThenClearsSession() {
        CartLine line = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CHECKOUT_CONFIRM).withCartLineAdded(line);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        tenant.setVendorNotificationPhoneNumber("+19998887777");
        OrderResponse orderResponse = new OrderResponse(
                100L, "ORD-2026-ABC123", customer.getId(), customer.getFullName(), customer.getPhoneNumber(),
                com.bot.whatsappbotservice.order.OrderStatus.NEW,
                com.bot.whatsappbotservice.order.OrderChannel.WHATSAPP, "INR", new BigDecimal("165.00"),
                new BigDecimal("165.00"), null,
                List.of(new com.bot.whatsappbotservice.order.dto.OrderItemResponse(
                        1L, 10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3), new BigDecimal("165.00"))),
                java.time.Instant.now());
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(orderResponse);

        conversationService.handleMessage(tenant, customer, "wamid-6", null, "CONFIRM");

        verify(orderService).createOrder(any(CreateOrderRequest.class));
        verify(sessionStore).clear(TENANT_ID, PHONE);
        verify(sessionStore, never()).save(any(), any(), any());
        // Exact-content checks confirm real MessageFormat substitution for the {0}/{1}/{2}
        // placeholders in "bot.order.placed" and "bot.vendor.new_order" — not just that some
        // string was sent. Also pins down that the final receipt lists line items (product name,
        // quantity, line total), not just the order-level total.
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Thank you! Your order ORD-2026-ABC123 has been placed.\n- Milk x 3 = 165.00\n\nTotal: 165.00 INR"));
        verify(messagingService).sendText(eq(tenant), eq((Customer) null), eq("+19998887777"),
                eq("New order received: ORD-2026-ABC123 from +14155550100 (+14155550100). Total: 165.00 INR. "
                        + "Please review and confirm it in your dashboard."));
    }

    @Test
    void insufficientStockDuringCheckoutRestartsFlowInsteadOfCrashing() {
        CartLine line = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CHECKOUT_CONFIRM).withCartLineAdded(line);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(orderService.createOrder(any(CreateOrderRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Insufficient stock"));
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of()));

        conversationService.handleMessage(tenant, customer, "wamid-7", null, "CONFIRM");

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CATEGORY_SELECTION);
        assertThat(captor.getValue().cart()).isEmpty();
        verify(sessionStore, never()).clear(any(), any());

        // Exact-content check (not just anyString()): "bot.order.failed" is the one message-bundle
        // key that combines a {0} placeholder with literal apostrophes ("couldn't"/"Let's") in the
        // same string, which MessageFormat would otherwise silently mangle if they weren't escaped
        // as '' in messages_en.properties. This confirms the real ResourceBundleMessageSource
        // renders it correctly end-to-end, not just that some string was sent.
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingService, times(2)).sendText(eq(tenant), eq(customer), eq(PHONE), textCaptor.capture());
        assertThat(textCaptor.getAllValues().get(0))
                .isEqualTo("Sorry, we couldn't place your order: Insufficient stock. Let's start again.");
    }

    private OrderResponse sampleOrder(String orderNumber, OrderStatus status, BigDecimal total, Instant createdAt) {
        return new OrderResponse(1L, orderNumber, customer.getId(), null, customer.getPhoneNumber(), status,
                OrderChannel.WHATSAPP, "INR", total, total, null, List.of(), createdAt);
    }

    @Test
    void typingOrdersShowsRecentOrdersAndHistoryPeriodButtons() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(orderService.listRecentForCustomer(eq(customer.getId()), eq(3))).thenReturn(List.of(
                sampleOrder("ORD-1", OrderStatus.DELIVERED, new BigDecimal("100.00"), Instant.now()),
                sampleOrder("ORD-2", OrderStatus.NEW, new BigDecimal("50.00"), Instant.now())));

        conversationService.handleMessage(tenant, customer, "wamid-orders-1", "orders", null);

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.ORDER_HISTORY_MENU);
        assertThat(captor.getValue().lastOptionIds()).containsExactly("HISTORY_WEEK", "HISTORY_MONTH", "HISTORY_YEAR");
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                argThat(text -> text.contains("ORD-1") && text.contains("ORD-2")));
        verify(messagingService).sendInteractiveButtons(eq(tenant), eq(customer), eq(PHONE), anyString(), anyList(),
                anyString());
    }

    @Test
    void ordersTriggerWithNoOrdersLeavesSessionUntouched() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(orderService.listRecentForCustomer(eq(customer.getId()), eq(3))).thenReturn(List.of());

        conversationService.handleMessage(tenant, customer, "wamid-orders-2", "my orders", null);

        // No history to page through — leaving the session untouched (no step change, no
        // Week/Month/Year buttons for an empty history) means whatever the customer was doing
        // before is still exactly where they left it.
        verify(sessionStore, never()).save(any(), any(), any());
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("You haven't placed any orders yet. Type 'menu' to browse and place your first order!"));
        verify(messagingService, never()).sendInteractiveButtons(any(), any(), any(), any(), any(), any());
    }

    @Test
    void historyPeriodButtonSendsPdfDocumentForMetaTenant() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.ORDER_HISTORY_MENU)
                .withLastOptionIds(List.of("HISTORY_WEEK", "HISTORY_MONTH", "HISTORY_YEAR"));
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(orderService.listForCustomerBetween(eq(customer.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(sampleOrder("ORD-1", OrderStatus.DELIVERED, new BigDecimal("100.00"), Instant.now())));
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of()));

        conversationService.handleMessage(tenant, customer, "wamid-orders-3", null, "HISTORY_WEEK");

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(messagingService).sendDocument(eq(tenant), eq(customer), eq(PHONE), pdfCaptor.capture(),
                eq("order-history.pdf"), anyString());
        byte[] pdfBytes = pdfCaptor.getValue();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        // Lands back on the browsing hub afterward, same as every other mid-flow detour.
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Sorry, no categories are available right now. Please check back later."));
    }

    @Test
    void historyPeriodButtonFallsBackForTwilioTenant() {
        tenant.setMessagingProvider(MessagingProvider.TWILIO);
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.ORDER_HISTORY_MENU)
                .withLastOptionIds(List.of("HISTORY_WEEK", "HISTORY_MONTH", "HISTORY_YEAR"));
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of()));

        conversationService.handleMessage(tenant, customer, "wamid-orders-4", null, "HISTORY_MONTH");

        verify(messagingService, never()).sendDocument(any(), any(), any(), any(), any(), any());
        verify(orderService, never()).listForCustomerBetween(any(), any(), any());
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Downloadable order history isn't available on this channel yet — please contact us directly."));
    }

    @Test
    void productCodeEntryFromCategorySelectionSkipsStraightToQuantityEntry() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product milk = new Product();
        milk.setId(10L);
        milk.setActive(true);
        milk.setName("Milk");
        milk.setUnit("ltr");
        milk.setPrice(new BigDecimal("55.00"));
        when(productRepository.findBySkuIgnoreCase("milk123")).thenReturn(Optional.of(milk));
        Inventory inventory = new Inventory();
        inventory.setQuantityOnHand(new BigDecimal("20"));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        conversationService.handleMessage(tenant, customer, "wamid-code-1", "MILK123", null);

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.QUANTITY_ENTRY);
        assertThat(captor.getValue().selectedProductId()).isEqualTo(10L);
        verify(categoryRepository, never()).findByActiveTrue(any());
        verify(productRepository, never()).findByCategoryId(any(), any());
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE), anyString());
    }

    @Test
    void productCodeEntryForOutOfStockProductRepromptsCategoryList() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product milk = new Product();
        milk.setId(10L);
        milk.setActive(true);
        milk.setName("Milk");
        when(productRepository.findBySkuIgnoreCase("milk123")).thenReturn(Optional.of(milk));
        Inventory inventory = new Inventory();
        inventory.setQuantityOnHand(BigDecimal.ZERO);
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of()));

        conversationService.handleMessage(tenant, customer, "wamid-code-2", "milk123", null);

        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CATEGORY_SELECTION);
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Sorry, Milk just went out of stock. Please choose another product."));
    }

    @Test
    void unrecognizedTextDuringCategorySelectionFallsBackToNormalInvalidHandling() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CATEGORY_SELECTION);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        when(productRepository.findBySkuIgnoreCase("not-a-code")).thenReturn(Optional.empty());
        when(categoryRepository.findByActiveTrue(any())).thenReturn(new PageImpl<>(List.of()));

        conversationService.handleMessage(tenant, customer, "wamid-code-3", "not-a-code", null);

        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Please select a category from the list."));
    }

    @Test
    void productCodeEntryIgnoredDuringQuantityEntrySoNumericFallbackStillWins() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.QUANTITY_ENTRY)
                .withCategory(5L).withSelectedProduct(10L);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        Product milk = new Product();
        milk.setId(10L);
        milk.setActive(true);
        milk.setName("Milk");
        milk.setUnit("ltr");
        milk.setPrice(new BigDecimal("55.00"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(milk));

        conversationService.handleMessage(tenant, customer, "wamid-code-4", "3", null);

        verify(productRepository, never()).findBySkuIgnoreCase(any());
        ArgumentCaptor<WhatsAppSession> captor = ArgumentCaptor.forClass(WhatsAppSession.class);
        verify(sessionStore).save(eq(TENANT_ID), eq(PHONE), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(ConversationStep.CART_REVIEW);
    }

    @Test
    void helpTriggerMidQuantityEntryDoesNotDisturbInProgressSession() {
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.QUANTITY_ENTRY)
                .withCategory(5L).withSelectedProduct(10L);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));

        conversationService.handleMessage(tenant, customer, "wamid-help-1", "help", null);

        // Help is a pure side channel: no session save at all, so the quantity-entry prompt the
        // customer was already looking at is still exactly what they should reply to next.
        verify(sessionStore, never()).save(any(), any(), any());
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                argThat(text -> text.contains("menu") && text.contains("orders")));
    }
}
