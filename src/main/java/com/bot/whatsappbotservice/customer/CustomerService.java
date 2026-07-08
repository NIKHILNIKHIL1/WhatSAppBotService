package com.bot.whatsappbotservice.customer;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.customer.dto.CreateCustomerRequest;
import com.bot.whatsappbotservice.customer.dto.CustomerResponse;
import com.bot.whatsappbotservice.customer.dto.UpdateCustomerRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AuditService auditService;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper,
                            AuditService auditService, PlatformTransactionManager transactionManager) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.auditService = auditService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(
                TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        if (customerRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException(
                    "A customer with phone number '" + request.phoneNumber() + "' already exists");
        }
        Customer customer = new Customer();
        customer.setPhoneNumber(request.phoneNumber());
        customer.setFullName(request.fullName());
        customer.setPreferredLanguageCode(defaultLanguage(request.preferredLanguageCode()));
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = getOrThrow(id);
        CustomerStatus oldStatus = customer.getStatus();

        customer.setFullName(request.fullName());
        if (StringUtils.hasText(request.preferredLanguageCode())) {
            customer.setPreferredLanguageCode(request.preferredLanguageCode());
        }
        customer.setStatus(request.status());
        customer = customerRepository.save(customer);

        if (oldStatus != customer.getStatus()) {
            auditService.record("Customer", id.toString(), AuditAction.UPDATE,
                    Map.of("status", oldStatus), Map.of("status", customer.getStatus()), AuditChannel.API);
        }
        return customerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return customerMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(Pageable pageable) {
        return customerRepository.findAll(pageable).map(customerMapper::toResponse);
    }

    @Transactional
    public void block(Long id) {
        Customer customer = getOrThrow(id);
        CustomerStatus oldStatus = customer.getStatus();
        customer.setStatus(CustomerStatus.BLOCKED);
        customerRepository.save(customer);
        auditService.record("Customer", id.toString(), AuditAction.UPDATE,
                Map.of("status", oldStatus), Map.of("status", CustomerStatus.BLOCKED), AuditChannel.API);
    }

    /**
     * Used by the WhatsApp inbound flow to auto-register a customer on first contact. The insert
     * runs in its own {@code REQUIRES_NEW} transaction: if two webhook deliveries race and one
     * loses on the unique phone-number constraint, that failed transaction is rolled back in
     * isolation so the fallback read below isn't run inside an already-aborted Postgres
     * transaction (which would itself fail with "current transaction is aborted").
     *
     * <p>{@code displayName} is the sender's WhatsApp profile name (Meta's {@code contacts[].profile.name}
     * or Twilio's {@code ProfileName}) so the vendor can see who placed an order without the
     * customer ever having filled in a name themselves. It's only used to fill in a name that's
     * currently missing — never overwrites a name a vendor has since edited.
     */
    public Customer findOrCreateByPhoneNumber(String phoneNumber, String preferredLanguageCode, String displayName) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .map(customer -> backfillNameIfMissing(customer, displayName))
                .orElseGet(() -> createNewIsolated(phoneNumber, preferredLanguageCode, displayName));
    }

    private Customer backfillNameIfMissing(Customer customer, String displayName) {
        if (!StringUtils.hasText(customer.getFullName()) && StringUtils.hasText(displayName)) {
            customer.setFullName(displayName);
            return customerRepository.save(customer);
        }
        return customer;
    }

    private Customer createNewIsolated(String phoneNumber, String preferredLanguageCode, String displayName) {
        try {
            return requiresNewTransactionTemplate.execute(status -> {
                Customer customer = new Customer();
                customer.setPhoneNumber(phoneNumber);
                customer.setPreferredLanguageCode(defaultLanguage(preferredLanguageCode));
                if (StringUtils.hasText(displayName)) {
                    customer.setFullName(displayName);
                }
                return customerRepository.save(customer);
            });
        } catch (DataIntegrityViolationException ex) {
            return customerRepository.findByPhoneNumber(phoneNumber).orElseThrow(() -> ex);
        }
    }

    private static String defaultLanguage(String preferredLanguageCode) {
        return StringUtils.hasText(preferredLanguageCode) ? preferredLanguageCode : "en";
    }

    private Customer getOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
    }
}
