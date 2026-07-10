package com.bot.whatsappbotservice.inventory;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import com.bot.whatsappbotservice.inventory.dto.InventoryOverviewResponse;
import com.bot.whatsappbotservice.inventory.dto.InventoryResponse;
import com.bot.whatsappbotservice.inventory.dto.InventoryTransactionResponse;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stock adjustments are the hottest write path in the system (every order line touches it), so
 * concurrent adjustments to the same product are expected. {@link #doAdjust} takes a
 * {@code PESSIMISTIC_WRITE} lock on the inventory row, so concurrent writers queue on the row
 * instead of colliding. {@code Inventory}'s {@code @Version} column stays as a safety net against
 * any future write path that skips the lock, backed by a bounded retry on
 * {@link OptimisticLockingFailureException} — but that retry only runs for standalone calls (each
 * attempt in its own transaction). Calls that arrive inside an ambient transaction (order
 * creation/cancellation) must join it so stock moves atomically with the order, and retrying
 * there could never work anyway: the first failure marks the shared transaction rollback-only and
 * poisons the persistence context, so every subsequent attempt is doomed before it starts.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryMapper inventoryMapper;
    private final TransactionTemplate transactionTemplate;

    public InventoryService(InventoryRepository inventoryRepository,
                             InventoryTransactionRepository inventoryTransactionRepository,
                             InventoryMapper inventoryMapper,
                             PlatformTransactionManager transactionManager) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.inventoryMapper = inventoryMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public InventoryResponse get(Long productId) {
        return inventoryMapper.toResponse(getInventoryOrThrow(productId));
    }

    public Page<InventoryTransactionResponse> history(Long productId, Pageable pageable) {
        Inventory inventory = getInventoryOrThrow(productId);
        return inventoryTransactionRepository.findByInventoryIdOrderByCreatedAtDesc(inventory.getId(), pageable)
                .map(inventoryMapper::toResponse);
    }

    // @Transactional (readOnly) here — unlike get()/history() above, this needs product.name/sku,
    // not just product.id, so the lazy association must actually be initialized while the session
    // is still open (a Hibernate proxy can answer .getId() without hitting the DB, but not .getName()).
    @Transactional(readOnly = true)
    public Page<InventoryOverviewResponse> listWithProducts(Pageable pageable) {
        return inventoryRepository.findAllWithProduct(pageable).map(inventory -> new InventoryOverviewResponse(
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getQuantityOnHand(),
                inventory.getReorderLevel()));
    }

    public InventoryTransactionResponse adjustStock(Long productId, AdjustStockRequest request) {
        // See class javadoc: inside an ambient transaction, join it (no template, no retry).
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return doAdjust(productId, request);
        }
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> doAdjust(productId, request));
            } catch (OptimisticLockingFailureException ex) {
                lastFailure = ex;
                log.warn("Optimistic lock conflict adjusting stock for product {} (attempt {}/{}); retrying",
                        productId, attempt + 1, MAX_ATTEMPTS);
            }
        }
        log.error("Exhausted {} retries adjusting stock for product {}; giving up", MAX_ATTEMPTS, productId);
        throw lastFailure;
    }

    private InventoryTransactionResponse doAdjust(Long productId, AdjustStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inventory for product", productId));

        BigDecimal newQuantity = inventory.getQuantityOnHand().add(request.quantityDelta());
        if (newQuantity.signum() < 0) {
            log.warn("Insufficient stock for product {}: have {}, requested change {}", productId,
                    inventory.getQuantityOnHand(), request.quantityDelta());
            throw new BusinessRuleViolationException(
                    "Insufficient stock for product " + productId + ": have " + inventory.getQuantityOnHand()
                            + ", requested change " + request.quantityDelta());
        }
        inventory.setQuantityOnHand(newQuantity);
        // saveAndFlush (not save) so the version check happens now: when this runs nested inside a
        // larger transaction (e.g. order creation), Hibernate would otherwise defer the UPDATE — and
        // its optimistic-lock check — until that outer transaction commits, long after this retry
        // loop has already returned and stopped watching for OptimisticLockingFailureException.
        inventoryRepository.saveAndFlush(inventory);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setInventory(inventory);
        transaction.setProduct(inventory.getProduct());
        transaction.setTransactionType(request.transactionType());
        transaction.setQuantityDelta(request.quantityDelta());
        transaction.setQuantityAfter(newQuantity);
        transaction.setReferenceType(request.referenceType());
        transaction.setReferenceId(request.referenceId());
        transaction.setNotes(request.notes());

        return inventoryMapper.toResponse(inventoryTransactionRepository.save(transaction));
    }

    private Inventory getInventoryOrThrow(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inventory for product", productId));
    }
}
