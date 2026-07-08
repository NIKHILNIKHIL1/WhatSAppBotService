package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WhatsAppWebhookServiceTest {

    private static final String PHONE_NUMBER_ID = "PHONE_ID_123";

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private WhatsAppMessageRepository whatsAppMessageRepository;
    @Mock
    private WhatsAppConversationService conversationService;

    private WhatsAppWebhookService webhookService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        webhookService = new WhatsAppWebhookService(
                tenantRepository, customerService, whatsAppMessageRepository, conversationService,
                new ObjectMapper());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void unknownPhoneNumberIdIsDroppedWithoutError() {
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_NUMBER_ID)).thenReturn(Optional.empty());

        webhookService.processIncoming(textMessagePayload("wamid.1", "hello"));

        verify(customerService, never()).findOrCreateByPhoneNumber(anyString(), any(), any());
        verify(conversationService, never()).handleMessage(any(), any(), anyString(), any(), any());
    }

    @Test
    void duplicateMessageIdIsSkipped() {
        Tenant tenant = tenant();
        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_NUMBER_ID)).thenReturn(Optional.of(tenant));
        when(whatsAppMessageRepository.existsByWaMessageId("wamid.1")).thenReturn(true);

        webhookService.processIncoming(textMessagePayload("wamid.1", "hello"));

        verify(customerService, never()).findOrCreateByPhoneNumber(anyString(), any(), any());
        verify(whatsAppMessageRepository, never()).save(any());
        verify(conversationService, never()).handleMessage(any(), any(), anyString(), any(), any());
    }

    @Test
    void statusOnlyPayloadIsIgnored() {
        String json = """
                {"entry":[{"changes":[{"value":{
                    "metadata": {"phone_number_id": "PHONE_ID_123"},
                    "statuses": [{"id": "wamid.out1", "status": "delivered"}]
                }}]}]}
                """;
        WhatsAppWebhookPayload payload = parse(json);

        webhookService.processIncoming(payload);

        verify(tenantRepository, never()).findByWhatsappPhoneNumberId(any());
    }

    @Test
    void resolvesTenantSetsTenantContextDuringProcessingThenClearsIt() {
        Tenant tenant = tenant();
        Customer customer = new Customer();
        customer.setId(9L);
        customer.setPhoneNumber("14155550100");

        when(tenantRepository.findByWhatsappPhoneNumberId(PHONE_NUMBER_ID)).thenReturn(Optional.of(tenant));
        when(whatsAppMessageRepository.existsByWaMessageId("wamid.1")).thenReturn(false);
        when(customerService.findOrCreateByPhoneNumber(eq("14155550100"), any(), any())).thenReturn(customer);

        AtomicReference<Long> tenantIdDuringProcessing = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            tenantIdDuringProcessing.set(TenantContext.getTenantId());
            return null;
        }).when(conversationService).handleMessage(eq(tenant), eq(customer), eq("wamid.1"), eq("hello"), isNull());

        webhookService.processIncoming(textMessagePayload("wamid.1", "hello"));

        assertThat(tenantIdDuringProcessing.get()).isEqualTo(tenant.getId());
        assertThat(TenantContext.getTenantId()).isNull();
        verify(whatsAppMessageRepository).save(any(WhatsAppMessage.class));
        verify(conversationService).handleMessage(tenant, customer, "wamid.1", "hello", null);
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(42L);
        tenant.setWhatsappPhoneNumberId(PHONE_NUMBER_ID);
        return tenant;
    }

    private WhatsAppWebhookPayload textMessagePayload(String messageId, String body) {
        String json = """
                {"entry":[{"changes":[{"value":{
                    "metadata": {"phone_number_id": "%s"},
                    "messages": [{"from": "14155550100", "id": "%s", "type": "text", "text": {"body": "%s"}}]
                }}]}]}
                """.formatted(PHONE_NUMBER_ID, messageId, body);
        return parse(json);
    }

    private WhatsAppWebhookPayload parse(String json) {
        try {
            return new ObjectMapper().readValue(json, WhatsAppWebhookPayload.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
