package com.bot.whatsappbotservice.order;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.catalog.Product;
import com.bot.whatsappbotservice.catalog.ProductRepository;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.inventory.InventoryService;
import com.bot.whatsappbotservice.inventory.InventoryTransactionType;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import com.bot.whatsappbotservice.order.dto.ConcernResponse;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.order.dto.OrderStatusHistoryResponse;
import com.bot.whatsappbotservice.order.dto.OutstandingPaymentsSummary;
import com.bot.whatsappbotservice.order.dto.RecordPaymentRequest;
import com.bot.whatsappbotservice.order.dto.UpdateOrderStatusRequest;
import com.bot.whatsappbotservice.order.event.ConcernResolvedEvent;
import com.bot.whatsappbotservice.order.event.OrderStatusChangedEvent;
import com.bot.whatsappbotservice.order.event.PaymentRecordedEvent;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ORDER_NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** "Today" in date filters means the vendor's day, not UTC. Orders store UTC instants; this is
     * the zone whole-day filter bounds are computed in. Single-market deployment assumption: the
     * server (or container TZ env) runs in the vendors' timezone — revisit if tenants ever span
     * timezones, which would need a per-tenant zone column instead. */
    private static final ZoneId VENDOR_ZONE = ZoneId.systemDefault();

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderConcernRepository orderConcernRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final InventoryService inventoryService;
    private final OrderMapper orderMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderStatusHistoryRepository orderStatusHistoryRepository,
                         OrderConcernRepository orderConcernRepository,
                         CustomerRepository customerRepository, ProductRepository productRepository,
                         TenantRepository tenantRepository, InventoryService inventoryService,
                         OrderMapper orderMapper, AuditService auditService,
                         ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.orderConcernRepository = orderConcernRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.tenantRepository = tenantRepository;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (StringUtils.hasText(request.idempotencyKey())) {
            var existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.debug("Order creation replayed for idempotency key {}; returning existing order {}",
                        request.idempotencyKey(), existing.get().getOrderNumber());
                return orderMapper.toResponse(existing.get());
            }
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));

        OrderHeader order = new OrderHeader();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.NEW);
        order.setChannel(request.channel() != null ? request.channel() : OrderChannel.API);
        order.setCurrencyCode(resolveTenantCurrency());
        order.setNotes(request.notes());
        order.setIdempotencyKey(request.idempotencyKey());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", itemRequest.productId()));

            BigDecimal lineTotal = product.getPrice().multiply(itemRequest.quantity());
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(product.getPrice());
            item.setQuantity(itemRequest.quantity());
            item.setLineTotal(lineTotal);
            order.addItem(item);

            subtotal = subtotal.add(lineTotal);
        }
        order.setSubtotalAmount(subtotal);
        order.setTotalAmount(subtotal);

        // Insert first (IDENTITY generation makes order.getId() available immediately) so stock
        // deductions below can reference the order id, and so the whole thing rolls back together
        // if any line item has insufficient stock.
        order = orderRepository.save(order);

        recordStatusHistory(order, null, OrderStatus.NEW, "Order created");

        // Deduct in ascending product-id order: adjustStock takes a row lock per inventory row,
        // and two concurrent multi-line orders locking the same products in different sequences
        // would deadlock. A single global ordering makes one simply wait for the other.
        for (OrderItem item : itemsInLockOrder(order)) {
            AdjustStockRequest deduction = new AdjustStockRequest(
                    InventoryTransactionType.SALE, item.getQuantity().negate(), "ORDER", order.getId(),
                    "Order " + order.getOrderNumber());
            inventoryService.adjustStock(item.getProduct().getId(), deduction);
        }

        auditService.record("OrderHeader", order.getId().toString(), AuditAction.CREATE, null,
                Map.of("orderNumber", order.getOrderNumber(), "status", order.getStatus(),
                        "totalAmount", order.getTotalAmount()),
                AuditChannel.valueOf(order.getChannel().name()));

        log.info("Order {} created via {} for customer {}: {} item(s), total {} {}", order.getOrderNumber(),
                order.getChannel(), customer.getId(), order.getItems().size(), order.getTotalAmount(),
                order.getCurrencyCode());

        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        OrderHeader order = getOrThrow(orderId);
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();

        if (!from.canTransitionTo(to)) {
            log.warn("Rejected invalid status transition for order {}: {} -> {}", order.getOrderNumber(), from, to);
            throw new BusinessRuleViolationException("Cannot transition order from " + from + " to " + to);
        }

        order.setStatus(to);
        orderRepository.save(order);
        recordStatusHistory(order, from, to, request.notes());
        log.info("Order {} status changed {} -> {}", order.getOrderNumber(), from, to);

        if (to == OrderStatus.CANCELLED) {
            log.info("Order {} cancelled; releasing stock for {} item(s)", order.getOrderNumber(),
                    order.getItems().size());
            // Same ascending product-id lock order as the deduction loop in createOrder.
            for (OrderItem item : itemsInLockOrder(order)) {
                AdjustStockRequest release = new AdjustStockRequest(
                        InventoryTransactionType.RETURN, item.getQuantity(), "ORDER", order.getId(),
                        "Order " + order.getOrderNumber() + " cancelled");
                inventoryService.adjustStock(item.getProduct().getId(), release);
            }
        }

        auditService.record("OrderHeader", order.getId().toString(), AuditAction.UPDATE,
                Map.of("status", from), Map.of("status", to), AuditChannel.API);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(TenantContext.getTenantId(), order.getId(), from, to));

        return orderMapper.toResponse(order);
    }

    /**
     * Registers a (possibly partial) payment against an order. Payment status is derived, never
     * set by the caller: the running {@code amountPaid} against {@code totalAmount} decides
     * PARTIALLY_PAID vs PAID, and overpayment is rejected outright. Publishing
     * {@link PaymentRecordedEvent} after the fact keeps the WhatsApp "payment received"
     * notification out of this module, mirroring how status changes notify.
     */
    @Transactional
    public OrderResponse recordPayment(Long orderId, RecordPaymentRequest request) {
        OrderHeader order = getOrThrow(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException(
                    "Cannot record a payment on cancelled order " + order.getOrderNumber());
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessRuleViolationException("Order " + order.getOrderNumber() + " is already fully paid");
        }
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessRuleViolationException(
                    "Order " + order.getOrderNumber() + " was refunded; no further payments can be recorded");
        }
        BigDecimal newAmountPaid = order.getAmountPaid().add(request.amount());
        if (newAmountPaid.compareTo(order.getTotalAmount()) > 0) {
            throw new BusinessRuleViolationException("Payment of " + request.amount() + " exceeds the outstanding "
                    + "balance of " + order.getTotalAmount().subtract(order.getAmountPaid())
                    + " on order " + order.getOrderNumber());
        }

        PaymentStatus from = order.getPaymentStatus();
        PaymentStatus to = newAmountPaid.compareTo(order.getTotalAmount()) == 0
                ? PaymentStatus.PAID
                : PaymentStatus.PARTIALLY_PAID;
        order.setAmountPaid(newAmountPaid);
        order.setPaymentMethod(request.method());
        if (StringUtils.hasText(request.reference())) {
            order.setPaymentReference(request.reference());
        }
        order.setPaymentStatus(to);
        if (to == PaymentStatus.PAID) {
            order.setPaidAt(Instant.now());
        }
        orderRepository.save(order);

        auditService.record("OrderHeader", order.getId().toString(), AuditAction.UPDATE,
                Map.of("paymentStatus", from),
                Map.of("paymentStatus", to, "amountPaid", newAmountPaid, "paymentMethod", request.method()),
                AuditChannel.API);
        log.info("Order {} payment recorded: {} via {} ({} -> {}, paid {} of {})", order.getOrderNumber(),
                request.amount(), request.method(), from, to, newAmountPaid, order.getTotalAmount());
        eventPublisher.publishEvent(new PaymentRecordedEvent(TenantContext.getTenantId(), order.getId(), to));

        return orderMapper.toResponse(order);
    }

    /** Marks the whole recorded payment as returned to the customer — a bookkeeping flag for
     * cancelled-after-payment orders, not a money movement (this system doesn't hold funds). */
    @Transactional
    public OrderResponse refundPayment(Long orderId) {
        OrderHeader order = getOrThrow(orderId);
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessRuleViolationException("Order " + order.getOrderNumber() + " is already refunded");
        }
        if (order.getAmountPaid().signum() <= 0) {
            throw new BusinessRuleViolationException(
                    "Order " + order.getOrderNumber() + " has no recorded payment to refund");
        }
        PaymentStatus from = order.getPaymentStatus();
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepository.save(order);

        auditService.record("OrderHeader", order.getId().toString(), AuditAction.UPDATE,
                Map.of("paymentStatus", from), Map.of("paymentStatus", PaymentStatus.REFUNDED), AuditChannel.API);
        log.info("Order {} payment marked refunded ({} had been paid)", order.getOrderNumber(),
                order.getAmountPaid());

        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return orderMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, Pageable pageable) {
        return list(status, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, PaymentStatus paymentStatus, Pageable pageable) {
        return list(status, paymentStatus, null, null, pageable);
    }

    /** All filters optional and freely combinable; dates are whole vendor-local days (see
     * {@link #VENDOR_ZONE}), inclusive on both ends. Reversed bounds are swapped rather than
     * rejected — a filter form should never error over which box a date was typed into. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, PaymentStatus paymentStatus,
                                     LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }
        Instant from = fromDate != null ? fromDate.atStartOfDay(VENDOR_ZONE).toInstant() : null;
        Instant to = toDate != null ? toDate.plusDays(1).atStartOfDay(VENDOR_ZONE).toInstant() : null;
        return orderRepository.findAll(filterSpec(status, paymentStatus, from, to), pageable)
                .map(orderMapper::toResponse);
    }

    private static Specification<OrderHeader> filterSpec(OrderStatus status, PaymentStatus paymentStatus,
                                                          Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Revenue (order totals, cancelled excluded) for whole vendor-local days [fromDate, toDate]. */
    @Transactional(readOnly = true)
    public BigDecimal revenueBetween(LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate.atStartOfDay(VENDOR_ZONE).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(VENDOR_ZONE).toInstant();
        BigDecimal revenue = orderRepository.sumTotalAmountBetween(from, to, OrderStatus.CANCELLED);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public OutstandingPaymentsSummary outstandingPayments() {
        List<PaymentStatus> owing = List.of(PaymentStatus.UNPAID, PaymentStatus.PARTIALLY_PAID);
        long count = orderRepository.countByPaymentStatusInAndStatusNot(owing, OrderStatus.CANCELLED);
        BigDecimal amount = orderRepository.sumOutstandingAmount(owing, OrderStatus.CANCELLED);
        return new OutstandingPaymentsSummary(count, amount != null ? amount : BigDecimal.ZERO);
    }

    /** Storefront-facing: a customer's own orders only, never another customer's or the full
     * tenant list — {@link #list} stays vendor-facing and untouched. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listForCustomer(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(orderMapper::toResponse);
    }

    /** WhatsApp "my orders" — the customer's most recent orders, newest first. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listRecentForCustomer(Long customerId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findByCustomerId(customerId, pageable).map(orderMapper::toResponse).getContent();
    }

    /** WhatsApp order-history PDF export — every order for the customer within [from, to]. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listForCustomerBetween(Long customerId, Instant from, Instant to) {
        return orderRepository.findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(customerId, from, to).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    /** Records a customer-raised concern (WhatsApp photo about a delivery). {@code orderId} may be
     * null — the concern still gets tracked, just unpinned. Returns the order number for messaging,
     * or null when unpinned. */
    @Transactional
    public void recordConcern(Long orderId, Long customerId, String mediaReference, String caption) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
        OrderConcern concern = new OrderConcern();
        if (orderId != null) {
            concern.setOrder(getOrThrow(orderId));
        }
        concern.setCustomer(customer);
        concern.setMediaReference(mediaReference);
        concern.setCaption(caption);
        orderConcernRepository.save(concern);
        log.info("Concern recorded for customer {} (order {})", customerId, orderId);
    }

    @Transactional(readOnly = true)
    public List<ConcernResponse> listConcerns(Long orderId) {
        return orderConcernRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(c -> new ConcernResponse(c.getId(), c.getCaption(), c.getStatus(), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void resolveConcern(Long concernId) {
        OrderConcern concern = orderConcernRepository.findById(concernId)
                .orElseThrow(() -> ResourceNotFoundException.of("Concern", concernId));
        if (concern.getStatus() == ConcernStatus.RESOLVED) {
            // Idempotent: a double-clicked Resolve button must not message the customer twice.
            return;
        }
        concern.setStatus(ConcernStatus.RESOLVED);
        orderConcernRepository.save(concern);
        eventPublisher.publishEvent(new ConcernResolvedEvent(TenantContext.getTenantId(), concern.getId()));
    }

    @Transactional(readOnly = true)
    public Page<OrderStatusHistoryResponse> history(Long orderId, Pageable pageable) {
        getOrThrow(orderId);
        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(orderId, pageable)
                .map(orderMapper::toResponse);
    }

    private List<OrderItem> itemsInLockOrder(OrderHeader order) {
        return order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
    }

    private void recordStatusHistory(OrderHeader order, OrderStatus from, OrderStatus to, String notes) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setNotes(notes);
        orderStatusHistoryRepository.save(history);
    }

    private String resolveTenantCurrency() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return "INR";
        }
        return tenantRepository.findById(tenantId).map(Tenant::getCurrencyCode).orElse("INR");
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ORDER_NUMBER_ALPHABET.charAt(RANDOM.nextInt(ORDER_NUMBER_ALPHABET.length())));
        }
        return "ORD-" + datePart + "-" + suffix;
    }

    private OrderHeader getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }
}
