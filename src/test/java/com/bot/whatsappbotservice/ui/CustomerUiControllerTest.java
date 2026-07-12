package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.customer.CustomerStatus;
import com.bot.whatsappbotservice.customer.dto.CustomerResponse;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(CustomerUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    // UiModelAttributesAdvice (@ControllerAdvice over all of com.bot.whatsappbotservice.ui) needs
    // this on every UI slice test; without it the whole context fails to load.
    @MockitoBean
    private TenantRepository tenantRepository;

    @Test
    void listRendersCustomersTable() throws Exception {
        CustomerResponse jane = new CustomerResponse(1L, "+14155550100", "Jane Doe", "en", CustomerStatus.ACTIVE);
        when(customerService.list(any())).thenReturn(new PageImpl<>(List.of(jane)));

        MvcTestResult result = mvc.get().uri("/ui/customers").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Jane Doe").contains("+14155550100");
    }

    @Test
    void newFormRenders() throws Exception {
        MvcTestResult result = mvc.get().uri("/ui/customers/new").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("New Customer");
    }

    @Test
    void editFormMarksPhoneNumberReadonly() throws Exception {
        CustomerResponse jane = new CustomerResponse(1L, "+14155550100", "Jane Doe", "en", CustomerStatus.ACTIVE);
        when(customerService.get(1L)).thenReturn(jane);

        MvcTestResult result = mvc.get().uri("/ui/customers/1/edit").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Edit Customer").contains("readonly");
    }

    @Test
    void invalidPhoneNumberReRendersFormWithError() throws Exception {
        MvcTestResult result = mvc.post().uri("/ui/customers/new")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "not-a-phone")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void duplicatePhoneNumberReRendersFormWithError() throws Exception {
        doThrow(new DuplicateResourceException("A customer with phone number '+14155550100' already exists"))
                .when(customerService).create(ArgumentMatchers.any());

        MvcTestResult result = mvc.post().uri("/ui/customers/new")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "+14155550100")
                .param("fullName", "Jane Doe")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("already exists");
    }

    @Test
    void missingNameReRendersFormWithError() throws Exception {
        // Registration means name + number — a nameless customer can't be registered.
        MvcTestResult result = mvc.post().uri("/ui/customers/new")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "+14155550100")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
        verify(customerService, never()).create(ArgumentMatchers.any());
    }

    @Test
    void blockRedirectsToList() {
        MvcTestResult result = mvc.post().uri("/ui/customers/1/block").exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/customers");
    }
}
