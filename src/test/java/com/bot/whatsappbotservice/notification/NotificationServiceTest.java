package com.bot.whatsappbotservice.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.inventory.Inventory;
import com.bot.whatsappbotservice.inventory.InventoryRepository;
import com.bot.whatsappbotservice.order.OrderHeader;
import com.bot.whatsappbotservice.order.OrderItem;
import com.bot.whatsappbotservice.order.OrderRepository;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessagingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private com.bot.whatsappbotservice.order.OrderConcernRepository orderConcernRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private WhatsAppMessagingService whatsAppMessagingService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        org.springframework.context.support.ResourceBundleMessageSource messageSource =
                new org.springframework.context.support.ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        notificationService = new NotificationService(
                notificationRepository, orderRepository, orderConcernRepository, tenantRepository,
                inventoryRepository, whatsAppMessagingService,
                new com.bot.whatsappbotservice.i18n.WhatsAppMessages(messageSource));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void lowStockAlertGoesToVendorNumberAndIsRecorded() {
        Tenant tenant = tenant();
        tenant.setVendorNotificationPhoneNumber("+919999900000");
        tenant.setDefaultLanguageCode("en");
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        com.bot.whatsappbotservice.catalog.Product product = new com.bot.whatsappbotservice.catalog.Product();
        product.setName("Cream Milk");
        product.setSku("CRM-MLK");
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantityOnHand(new BigDecimal("7"));
        inventory.setReorderLevel(new BigDecimal("8"));
        when(inventoryRepository.findByProductId(42L)).thenReturn(Optional.of(inventory));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.SENT);

        notificationService.notifyLowStock(7L, 42L);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(whatsAppMessagingService).sendText(eq(tenant), any(), eq("+919999900000"), body.capture());
        assertThat(body.getValue()).contains("CRM-MLK").contains("7").contains("8");
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getTemplateCode()).isEqualTo("LOW_STOCK");
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void lowStockAlertSkippedWhenNoVendorNumberConfigured() {
        Tenant tenant = tenant();
        tenant.setVendorNotificationPhoneNumber(null);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        Inventory inventory = new Inventory();
        inventory.setProduct(new com.bot.whatsappbotservice.catalog.Product());
        when(inventoryRepository.findByProductId(42L)).thenReturn(Optional.of(inventory));

        notificationService.notifyLowStock(7L, 42L);

        verify(whatsAppMessagingService, never()).sendText(any(), any(), anyString(), anyString());
    }

    @Test
    void successfulSendMarksNotificationSent() {
        Tenant tenant = tenant();
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.SENT);

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(captor.getValue().getSentAt()).isNotNull();
    }

    @Test
    void failedSendMarksNotificationFailed() {
        Tenant tenant = tenant();
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.FAILED);

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getSentAt()).isNull();
    }

    @Test
    void skipsGracefullyWhenTenantHasNoWhatsAppConfigured() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        verify(whatsAppMessagingService, never()).sendText(any(), any(), anyString(), anyString());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void skipsGracefullyWhenOrderNotFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        verify(tenantRepository, never()).findById(any());
        verify(whatsAppMessagingService, never()).sendText(any(), any(), anyString(), anyString());
    }

    @Test
    void notifiesVendorWhenOrderConfirmedAndVendorNumberConfigured() {
        Tenant tenant = tenant();
        tenant.setVendorNotificationPhoneNumber("+19998887777");
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.SENT);

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        verify(whatsAppMessagingService).sendText(eq(tenant), any(), eq("+19998887777"), anyString());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        Notification vendorNotification = captor.getAllValues().stream()
                .filter(n -> n.getRecipientType() == RecipientType.VENDOR)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(vendorNotification.getRecipientId()).isEqualTo(7L);
        assertThat(vendorNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void skipsVendorNotificationWhenVendorNumberNotConfigured() {
        Tenant tenant = tenant();
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.SENT);

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        verify(whatsAppMessagingService, org.mockito.Mockito.times(1))
                .sendText(any(), any(), anyString(), anyString());
    }

    @Test
    void skipsVendorNotificationWhenStatusIsNotConfirmed() {
        Tenant tenant = tenant();
        tenant.setVendorNotificationPhoneNumber("+19998887777");
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.SENT);

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.CONFIRMED, OrderStatus.ACCEPTED);

        verify(whatsAppMessagingService, never()).sendText(any(), any(), eq("+19998887777"), anyString());
    }

    @Test
    void vendorNotificationFailureDoesNotAffectCustomerNotification() {
        Tenant tenant = tenant();
        tenant.setVendorNotificationPhoneNumber("+19998887777");
        OrderHeader order = order(tenant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        // Customer send succeeds; vendor send throws an unexpected runtime exception.
        when(whatsAppMessagingService.sendText(any(), any(), eq("+14155550100"), anyString()))
                .thenReturn(MessageStatus.SENT);
        when(whatsAppMessagingService.sendText(any(), any(), eq("+19998887777"), anyString()))
                .thenThrow(new RuntimeException("boom"));

        notificationService.notifyOrderStatusChange(7L, 1L, OrderStatus.NEW, OrderStatus.CONFIRMED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Notification customerNotification = captor.getAllValues().stream()
                .filter(n -> n.getRecipientType() == RecipientType.CUSTOMER)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(customerNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(customerNotification.getSentAt()).isNotNull();
    }

    @Test
    void concernResolvedNotifiesCustomerWithOrderReferenceInTheirLanguage() {
        Tenant tenant = tenant();
        OrderHeader order = order(tenant);
        com.bot.whatsappbotservice.order.OrderConcern concern = new com.bot.whatsappbotservice.order.OrderConcern();
        concern.setId(5L);
        concern.setOrder(order);
        concern.setCustomer(order.getCustomer());
        when(orderConcernRepository.findById(5L)).thenReturn(Optional.of(concern));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        when(whatsAppMessagingService.sendText(any(), any(), anyString(), anyString()))
                .thenReturn(MessageStatus.SENT);

        notificationService.notifyConcernResolved(7L, 5L);

        verify(whatsAppMessagingService).sendText(eq(tenant), eq(order.getCustomer()), eq("+14155550100"),
                eq("✅ Update from the shop: your concern about order ORD-1 has been resolved. "
                        + "Thank you for your patience!"));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("CONCERN_RESOLVED");
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setWhatsappPhoneNumberId("PHONE_ID");
        tenant.setWhatsappAccessToken("token");
        return tenant;
    }

    private OrderHeader order(Tenant tenant) {
        OrderHeader order = new OrderHeader();
        order.setId(1L);
        order.setOrderNumber("ORD-1");
        Customer customer = new Customer();
        customer.setId(3L);
        customer.setPhoneNumber("+14155550100");
        order.setCustomer(customer);
        return order;
    }
}
