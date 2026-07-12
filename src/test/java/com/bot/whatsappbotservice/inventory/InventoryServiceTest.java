package com.bot.whatsappbotservice.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.Product;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import com.bot.whatsappbotservice.inventory.dto.InventoryTransactionResponse;
import com.bot.whatsappbotservice.inventory.event.LowStockEvent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InventoryMapper inventoryMapper;
    private PlatformTransactionManager transactionManager;
    private InventoryService inventoryService;

    private static final Long PRODUCT_ID = 42L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        inventoryMapper = new InventoryMapperImpl();
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        inventoryService = new InventoryService(
                inventoryRepository, inventoryTransactionRepository, inventoryMapper, transactionManager,
                eventPublisher);
    }

    @Test
    void adjustStockAppliesDeltaAndWritesLedgerEntry() {
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.TEN)));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdjustStockRequest request = new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-3), "ORDER", 100L, "sold 3 units");

        InventoryTransactionResponse response = inventoryService.adjustStock(PRODUCT_ID, request);

        assertThat(response.quantityAfter()).isEqualByComparingTo("7");
        assertThat(response.transactionType()).isEqualTo(InventoryTransactionType.SALE);
    }

    @Test
    void adjustStockRejectsWhenResultingQuantityWouldBeNegative() {
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.valueOf(2))));

        AdjustStockRequest request = new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-5), "ORDER", 100L, null);

        assertThatThrownBy(() -> inventoryService.adjustStock(PRODUCT_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(inventoryRepository, never()).saveAndFlush(any());
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void adjustStockRetriesOnOptimisticLockConflictThenSucceeds() {
        // Each re-read reflects the true committed quantity (10) as if the failed attempt rolled back.
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenAnswer(inv -> Optional.of(inventory(BigDecimal.TEN)));
        AtomicInteger saveCalls = new AtomicInteger();
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> {
            if (saveCalls.getAndIncrement() == 0) {
                throw new OptimisticLockingFailureException("lost the race");
            }
            return inv.getArgument(0);
        });
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdjustStockRequest request = new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-3), "ORDER", 100L, null);

        InventoryTransactionResponse response = inventoryService.adjustStock(PRODUCT_ID, request);

        assertThat(response.quantityAfter()).isEqualByComparingTo("7");
        assertThat(saveCalls.get()).isEqualTo(2);
    }

    @Test
    void adjustStockJoinsAmbientTransactionInsteadOfOpeningItsOwn() {
        // When called inside an existing transaction (order creation/cancellation), the adjustment
        // must join it — never run through the TransactionTemplate — so stock moves atomically
        // with the order and a failure rolls the whole order back.
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.TEN)));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdjustStockRequest request = new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-3), "ORDER", 100L, null);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            InventoryTransactionResponse response = inventoryService.adjustStock(PRODUCT_ID, request);
            assertThat(response.quantityAfter()).isEqualByComparingTo("7");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void adjustStockPublishesLowStockEventWhenCrossingReorderLevel() {
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.TEN, BigDecimal.valueOf(8))));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.adjustStock(PRODUCT_ID, new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-3), "ORDER", 100L, null));

        verify(eventPublisher).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void adjustStockDoesNotAlertAgainWhileAlreadyBelowReorderLevel() {
        // Already at/below the level before this sale — the crossing alert already fired earlier.
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.valueOf(5), BigDecimal.valueOf(8))));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.adjustStock(PRODUCT_ID, new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-2), "ORDER", 100L, null));

        verify(eventPublisher, never()).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void adjustStockNeverAlertsWhenReorderLevelIsZero() {
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.TEN, BigDecimal.ZERO)));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.adjustStock(PRODUCT_ID, new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.TEN.negate(), "ORDER", 100L, null));

        verify(eventPublisher, never()).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void restockingDoesNotPublishLowStockEvent() {
        when(inventoryRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(inventory(BigDecimal.valueOf(2), BigDecimal.valueOf(8))));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.adjustStock(PRODUCT_ID, new AdjustStockRequest(
                InventoryTransactionType.RECEIPT, BigDecimal.valueOf(50), "PURCHASE", null, null));

        verify(eventPublisher, never()).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void updateReorderLevelPersistsNewLevel() {
        Inventory stored = inventory(BigDecimal.TEN, BigDecimal.ZERO);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(stored));
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.updateReorderLevel(PRODUCT_ID, BigDecimal.valueOf(12));

        assertThat(stored.getReorderLevel()).isEqualByComparingTo("12");
        verify(inventoryRepository).save(stored);
    }

    private Inventory inventory(BigDecimal quantityOnHand) {
        return inventory(quantityOnHand, BigDecimal.ZERO);
    }

    private Inventory inventory(BigDecimal quantityOnHand, BigDecimal reorderLevel) {
        Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setProduct(new Product());
        inventory.setQuantityOnHand(quantityOnHand);
        inventory.setReorderLevel(reorderLevel);
        return inventory;
    }
}
