package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.tenant.Tenant;
import java.math.BigDecimal;
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

        conversationService = new WhatsAppConversationService(
                sessionStore, messagingService, categoryRepository, productRepository, customerRepository,
                orderService, messages);

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
    void confirmingOrderCreatesOrderNotifiesCustomerAndVendorThenClearsSession() {
        CartLine line = new CartLine(10L, "Milk", new BigDecimal("55.00"), BigDecimal.valueOf(3));
        WhatsAppSession session = WhatsAppSession.initial().withStep(ConversationStep.CHECKOUT_CONFIRM).withCartLineAdded(line);
        when(sessionStore.get(TENANT_ID, PHONE)).thenReturn(Optional.of(session));
        tenant.setVendorNotificationPhoneNumber("+19998887777");
        OrderResponse orderResponse = new OrderResponse(
                100L, "ORD-2026-ABC123", customer.getId(), customer.getFullName(), customer.getPhoneNumber(),
                com.bot.whatsappbotservice.order.OrderStatus.NEW,
                com.bot.whatsappbotservice.order.OrderChannel.WHATSAPP, "INR", new BigDecimal("165.00"),
                new BigDecimal("165.00"), null, List.of(), java.time.Instant.now());
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(orderResponse);

        conversationService.handleMessage(tenant, customer, "wamid-6", null, "CONFIRM");

        verify(orderService).createOrder(any(CreateOrderRequest.class));
        verify(sessionStore).clear(TENANT_ID, PHONE);
        verify(sessionStore, never()).save(any(), any(), any());
        // Exact-content checks confirm real MessageFormat substitution for the {0}/{1}/{2}
        // placeholders in "bot.order.placed" and "bot.vendor.new_order" — not just that some
        // string was sent.
        verify(messagingService).sendText(eq(tenant), eq(customer), eq(PHONE),
                eq("Thank you! Your order ORD-2026-ABC123 has been placed. Total: 165.00 INR"));
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
}
