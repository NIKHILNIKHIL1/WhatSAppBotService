package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.customer.CustomerStatus;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.tenant.Tenant;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.support.ResourceBundleMessageSource;

class CustomerRegistrationGateTest {

    private static final String PHONE = "+14155550100";

    @Mock
    private CustomerService customerService;
    @Mock
    private WhatsAppSessionStore sessionStore;
    @Mock
    private WhatsAppMessagingService messagingService;

    private CustomerRegistrationGate gate;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        gate = new CustomerRegistrationGate(customerService, sessionStore, messagingService,
                new WhatsAppMessages(messageSource));

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setDefaultLanguageCode("en");
        tenant.setRequireCustomerRegistration(true);
    }

    private Customer customer(CustomerStatus status) {
        Customer customer = new Customer();
        customer.setId(9L);
        customer.setPhoneNumber(PHONE);
        customer.setStatus(status);
        return customer;
    }

    @Test
    void registeredActiveCustomerPassesThrough() {
        Customer registered = customer(CustomerStatus.ACTIVE);
        when(customerService.findRegisteredByPhoneNumber(PHONE, "Priya")).thenReturn(Optional.of(registered));

        Optional<Customer> result = gate.resolveTransactingCustomer(tenant, PHONE, "Priya");

        assertThat(result).contains(registered);
        verify(messagingService, never()).sendText(any(), any(), anyString(), anyString());
        verify(customerService, never()).findOrCreateByPhoneNumber(anyString(), any(), any());
    }

    @Test
    void unregisteredNumberIsRefusedWithOneNoticeIncludingVendorContact() {
        tenant.setVendorNotificationPhoneNumber("+19998887777");
        when(customerService.findRegisteredByPhoneNumber(PHONE, null)).thenReturn(Optional.empty());
        when(sessionStore.tryClaimRejectionNotice(eq(1L), eq(PHONE), any(Duration.class))).thenReturn(true);

        Optional<Customer> result = gate.resolveTransactingCustomer(tenant, PHONE, null);

        assertThat(result).isEmpty();
        verify(customerService, never()).findOrCreateByPhoneNumber(anyString(), any(), any());
        verify(messagingService).sendText(eq(tenant), isNull(), eq(PHONE),
                eq("Sorry, this WhatsApp number isn't registered with us yet, so we can't take orders "
                        + "from it. You can also reach us directly at +19998887777."));
    }

    @Test
    void repeatedMessagesFromUnregisteredNumberSendNoFurtherNotices() {
        when(customerService.findRegisteredByPhoneNumber(PHONE, null)).thenReturn(Optional.empty());
        when(sessionStore.tryClaimRejectionNotice(eq(1L), eq(PHONE), any(Duration.class))).thenReturn(false);

        Optional<Customer> result = gate.resolveTransactingCustomer(tenant, PHONE, null);

        assertThat(result).isEmpty();
        verify(messagingService, never()).sendText(any(), any(), anyString(), anyString());
    }

    @Test
    void blockedCustomerIsRefusedEvenInOpenOrderingMode() {
        tenant.setRequireCustomerRegistration(false);
        when(customerService.findRegisteredByPhoneNumber(PHONE, null))
                .thenReturn(Optional.of(customer(CustomerStatus.BLOCKED)));
        when(sessionStore.tryClaimRejectionNotice(eq(1L), eq(PHONE), any(Duration.class))).thenReturn(true);

        Optional<Customer> result = gate.resolveTransactingCustomer(tenant, PHONE, null);

        assertThat(result).isEmpty();
        verify(customerService, never()).findOrCreateByPhoneNumber(anyString(), any(), any());
        verify(messagingService).sendText(eq(tenant), isNull(), eq(PHONE), contains("isn't registered"));
    }

    @Test
    void openOrderingModeAutoRegistersUnknownNumbers() {
        tenant.setRequireCustomerRegistration(false);
        Customer created = customer(CustomerStatus.ACTIVE);
        when(customerService.findRegisteredByPhoneNumber(PHONE, "Priya")).thenReturn(Optional.empty());
        when(customerService.findOrCreateByPhoneNumber(PHONE, null, "Priya")).thenReturn(created);

        Optional<Customer> result = gate.resolveTransactingCustomer(tenant, PHONE, "Priya");

        assertThat(result).contains(created);
        verify(messagingService, never()).sendText(any(), any(), anyString(), anyString());
    }

    @Test
    void nullPhoneNumberIsRefusedSilently() {
        Optional<Customer> result = gate.resolveTransactingCustomer(tenant, null, null);

        assertThat(result).isEmpty();
        verify(messagingService, never()).sendText(any(), any(), anyString(), anyString());
    }
}
