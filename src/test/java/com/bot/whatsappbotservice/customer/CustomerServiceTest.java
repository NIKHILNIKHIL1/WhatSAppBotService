package com.bot.whatsappbotservice.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.audit.AuditService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    private CustomerMapper customerMapper;
    private CustomerService customerService;

    private static final String PHONE = "+14155552671";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        customerMapper = new CustomerMapperImpl();
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        AuditService auditService = mock(AuditService.class);
        customerService = new CustomerService(customerRepository, customerMapper, auditService, transactionManager);
    }

    @Test
    void findOrCreateReturnsExistingCustomerWithoutInserting() {
        Customer existing = new Customer();
        existing.setId(1L);
        existing.setPhoneNumber(PHONE);
        when(customerRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));

        Customer result = customerService.findOrCreateByPhoneNumber(PHONE, "en", null);

        assertThat(result).isSameAs(existing);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findOrCreateInsertsNewCustomerWhenNoneExists() {
        when(customerRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        Customer result = customerService.findOrCreateByPhoneNumber(PHONE, "hi", null);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getPreferredLanguageCode()).isEqualTo("hi");
        verify(customerRepository, times(1)).save(any());
    }

    @Test
    void findOrCreateInsertsNewCustomerWithDisplayNameAsFullName() {
        when(customerRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        Customer result = customerService.findOrCreateByPhoneNumber(PHONE, "en", "Priya Sharma");

        assertThat(result.getFullName()).isEqualTo("Priya Sharma");
    }

    @Test
    void findOrCreateBackfillsMissingNameOnExistingCustomer() {
        Customer existing = new Customer();
        existing.setId(1L);
        existing.setPhoneNumber(PHONE);
        when(customerRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = customerService.findOrCreateByPhoneNumber(PHONE, "en", "Priya Sharma");

        assertThat(result.getFullName()).isEqualTo("Priya Sharma");
        verify(customerRepository, times(1)).save(existing);
    }

    @Test
    void findOrCreateDoesNotOverwriteExistingName() {
        Customer existing = new Customer();
        existing.setId(1L);
        existing.setPhoneNumber(PHONE);
        existing.setFullName("Vendor-Corrected Name");
        when(customerRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(existing));

        Customer result = customerService.findOrCreateByPhoneNumber(PHONE, "en", "Priya Sharma");

        assertThat(result.getFullName()).isEqualTo("Vendor-Corrected Name");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findOrCreateFallsBackToReadAfterLosingRaceOnUniqueConstraint() {
        Customer wonByOtherRequest = new Customer();
        wonByOtherRequest.setId(7L);
        wonByOtherRequest.setPhoneNumber(PHONE);

        AtomicInteger findCalls = new AtomicInteger();
        when(customerRepository.findByPhoneNumber(PHONE)).thenAnswer(inv ->
                findCalls.getAndIncrement() == 0 ? Optional.empty() : Optional.of(wonByOtherRequest));
        when(customerRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate phone_number"));

        Customer result = customerService.findOrCreateByPhoneNumber(PHONE, "en", null);

        assertThat(result).isSameAs(wonByOtherRequest);
    }
}
