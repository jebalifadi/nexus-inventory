package com.nexus.core.writeoff;

import com.nexus.core.money.Money;
import com.nexus.core.sale.ExtraCosts;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shipment.ShipmentService;
import com.nexus.core.stock.InventoryLot;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.StockRestored;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes off goods that left stock without being sold. The piece goes to the trash like any removal,
 * and its real cost (purchase, shipping, repairs) is recorded as a loss that lowers profit. Cash does
 * not change: that money already left when the goods were bought.
 */
@Service
public class WriteOffService {

    private static final int MAX_REASON_LENGTH = 255;

    private final WriteOffRepository repository;
    private final StockService stock;
    private final ShipmentService shipments;
    private final ExtraCosts extraCosts;

    WriteOffService(WriteOffRepository repository, StockService stock, ShipmentService shipments,
                    ExtraCosts extraCosts) {
        this.repository = repository;
        this.stock = stock;
        this.shipments = shipments;
        this.extraCosts = extraCosts;
    }

    /** One piece tracked on its own: it leaves stock and its real cost becomes a loss. */
    @Transactional
    public WriteOff writeOffUnit(String unitPublicId, String reason, LocalDate date) {
        String why = requireReason(reason);
        LocalDate day = requireDate(date);
        InventoryUnit unit = stock.getUnit(unitPublicId);
        List<Money> parts = new ArrayList<>();
        parts.add(unit.unitCost());
        parts.addAll(shipments.shippingCostPerPieceOfUnit(unit.id()));
        parts.addAll(extraCosts.forUnit(unit.id()));
        stock.deleteUnit(unit.publicId(), why); // refuses sold pieces and pieces on their way
        String id = repository.insert(unit.id(), null, unit.productId(), 1, why, day, perCurrency(parts));
        return find(id);
    }

    /** What is left of a batch: it leaves stock and its real cost becomes a loss. */
    @Transactional
    public WriteOff writeOffLot(String lotPublicId, String reason, LocalDate date) {
        String why = requireReason(reason);
        LocalDate day = requireDate(date);
        InventoryLot lot = stock.getLot(lotPublicId);
        int quantity = lot.quantityRemaining();
        List<Money> parts = new ArrayList<>();
        parts.add(lot.unitCost().times(quantity));
        for (Money shipping : shipments.shippingCostPerPieceOfLot(lot.id())) {
            parts.add(shipping.times(quantity));
        }
        stock.deleteLot(lot.publicId(), why); // refuses batches with sales and batches on their way
        String id = repository.insert(null, lot.id(), lot.productId(), quantity, why, day, perCurrency(parts));
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<WriteOff> listWriteOffs() {
        return repository.findActive();
    }

    /** A piece restored from the trash was not lost after all. */
    @EventListener
    void onRestored(StockRestored restored) {
        if (restored.unitId() != null) {
            repository.cancelForUnit(restored.unitId());
        }
        if (restored.lotId() != null) {
            repository.cancelForLot(restored.lotId());
        }
    }

    private WriteOff find(String publicId) {
        return repository.findActive().stream().filter(w -> w.publicId().equals(publicId)).findFirst().orElseThrow();
    }

    /** Adds the parts up per currency: EUR and TND are never mixed without an exchange rate. */
    private static List<Money> perCurrency(List<Money> parts) {
        Map<String, Money> totals = new LinkedHashMap<>();
        for (Money part : parts) {
            totals.merge(part.currency().getCurrencyCode(), part, Money::plus);
        }
        return List.copyOf(totals.values());
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Say why the goods are written off, for example Damaged or Lost");
        }
        String clean = reason.strip();
        if (clean.length() > MAX_REASON_LENGTH) {
            throw new BusinessRuleException("Reason is too long (maximum " + MAX_REASON_LENGTH + " characters)");
        }
        return clean;
    }

    private static LocalDate requireDate(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        if (day.isAfter(LocalDate.now())) {
            throw new BusinessRuleException("A write-off cannot be in the future");
        }
        return day;
    }
}
