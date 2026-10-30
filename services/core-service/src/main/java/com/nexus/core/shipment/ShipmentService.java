package com.nexus.core.shipment;

import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import com.nexus.core.stock.InventoryLot;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.StockService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of shipping: sending goods from one location to another and receiving them.
 *
 * <p>When goods leave, they are no longer counted as stock anywhere. When the shipment arrives,
 * they are in stock at the destination. The shipping cost is split across the goods inside, so each
 * item knows its real (landed) cost.
 */
@Service
public class ShipmentService {

    /** A unit or lot being packed, before the cost is split. */
    private record Packed(Long unitId, Long lotId, int quantity, Money unitCost) {
    }

    private final ShipmentRepository repository;
    private final StockService stock;

    ShipmentService(ShipmentRepository repository, StockService stock) {
        this.repository = repository;
        this.stock = stock;
    }

    @Transactional
    public Shipment createShipment(long fromLocationId, long toLocationId, String carrier, String trackingNumber,
                                   Money cost, AllocationMethod method, LocalDate shippedDate,
                                   List<String> unitIds, List<String> lotIds) {
        if (fromLocationId == toLocationId) {
            throw new BusinessRuleException("A shipment must go to a different location");
        }
        stock.getLocation(fromLocationId);
        stock.getLocation(toLocationId);
        if (cost == null) {
            throw new BusinessRuleException("Shipping cost is required (use 0 for free shipping)");
        }
        if (cost.isNegative()) {
            throw new BusinessRuleException("Shipping cost cannot be negative");
        }
        List<String> units = unitIds == null ? List.of() : unitIds;
        List<String> lots = lotIds == null ? List.of() : lotIds;
        if (units.isEmpty() && lots.isEmpty()) {
            throw new BusinessRuleException("A shipment needs at least one unit or lot");
        }
        requireNoDuplicates(units, lots);
        AllocationMethod allocation = method == null ? AllocationMethod.BY_VALUE : method;

        List<Packed> packed = new ArrayList<>();
        for (String unitId : units) {
            InventoryUnit unit = stock.sendUnit(unitId, fromLocationId);
            packed.add(new Packed(unit.id(), null, 1, unit.unitCost()));
        }
        for (String lotId : lots) {
            InventoryLot lot = stock.sendLot(lotId, fromLocationId);
            packed.add(new Packed(null, lot.id(), lot.quantityRemaining(), lot.unitCost()));
        }

        List<Money> shares = split(cost, allocation, packed);
        long shipmentId = repository.insertShipment(fromLocationId, toLocationId, blankToNull(carrier),
                blankToNull(trackingNumber), cost.amount(), cost.currency().getCurrencyCode(), allocation,
                shippedDate == null ? LocalDate.now() : shippedDate);
        for (int i = 0; i < packed.size(); i++) {
            Packed item = packed.get(i);
            repository.insertContent(shipmentId, item.unitId(), item.lotId(), item.quantity(), shares.get(i).amount());
        }
        return assemble(repository.findById(shipmentId).orElseThrow());
    }

    /** The goods arrive: they are in stock again, at the destination. */
    @Transactional
    public Shipment markArrived(String publicId, LocalDate arrivedDate) {
        ShipmentRepository.ShipmentRow shipment = repository.lockByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Shipment " + publicId + " not found"));
        if (shipment.status() == ShipmentStatus.ARRIVED) {
            throw new BusinessRuleException("Shipment " + publicId + " has already arrived");
        }
        LocalDate date = arrivedDate == null ? LocalDate.now() : arrivedDate;
        if (date.isBefore(shipment.shippedDate())) {
            throw new BusinessRuleException("A shipment cannot arrive before it was sent ("
                    + shipment.shippedDate() + ")");
        }
        for (ShipmentRepository.ContentRow content : repository.findContents(shipment.id())) {
            if (content.unitId() != null) {
                stock.receiveShippedUnit(content.unitId(), shipment.toLocationId());
            } else {
                stock.receiveShippedLot(content.lotId(), shipment.toLocationId());
            }
        }
        repository.markArrived(shipment.id(), date);
        return assemble(repository.findById(shipment.id()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Shipment getShipment(String publicId) {
        return repository.findByPublicId(publicId)
                .map(this::assemble)
                .orElseThrow(() -> new NotFoundException("Shipment " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Shipment> listShipments() {
        return repository.findAll().stream().map(this::assemble).toList();
    }

    /** Shipping cost carried by one unit, one entry per shipment it travelled in. */
    @Transactional(readOnly = true)
    public List<Money> shippingCostPerPieceOfUnit(long unitId) {
        return perPiece(repository.findSharesOfUnit(unitId));
    }

    /** Shipping cost carried by one piece of a lot, one entry per shipment it travelled in. */
    @Transactional(readOnly = true)
    public List<Money> shippingCostPerPieceOfLot(long lotId) {
        return perPiece(repository.findSharesOfLot(lotId));
    }

    private static List<Money> perPiece(List<ShipmentRepository.ShippingShare> shares) {
        return shares.stream()
                .map(share -> new Money(share.allocated().divide(BigDecimal.valueOf(share.quantity()), 10,
                        RoundingMode.HALF_EVEN), java.util.Currency.getInstance(share.currency())))
                .toList();
    }

    private static List<Money> split(Money cost, AllocationMethod method, List<Packed> packed) {
        List<BigDecimal> weights;
        if (method == AllocationMethod.BY_QUANTITY) {
            weights = packed.stream().map(p -> BigDecimal.valueOf(p.quantity())).toList();
        } else {
            long currencies = packed.stream().map(p -> p.unitCost().currency()).distinct().count();
            if (currencies > 1) {
                throw new BusinessRuleException("BY_VALUE needs goods bought in one currency: use BY_QUANTITY "
                        + "for goods bought in different currencies");
            }
            weights = packed.stream()
                    .map(p -> p.unitCost().amount().multiply(BigDecimal.valueOf(p.quantity())))
                    .toList();
            if (cost.amount().signum() > 0 && weights.stream().allMatch(w -> w.signum() == 0)) {
                throw new BusinessRuleException("These goods cost nothing, so BY_VALUE cannot split the cost: "
                        + "use BY_QUANTITY");
            }
        }
        return CostAllocator.allocate(cost, weights);
    }

    private Shipment assemble(ShipmentRepository.ShipmentRow row) {
        List<ShipmentItem> items = repository.findContents(row.id()).stream().map(content -> {
            Money purchaseCost = Money.of(content.purchaseCostAmount().toPlainString(),
                    content.purchaseCostCurrency());
            Money allocated = Money.of(content.allocatedCost().toPlainString(), row.costCurrency());
            Money landed = null;
            if (purchaseCost.currency().equals(allocated.currency())) {
                BigDecimal shippingPerPiece = allocated.amount()
                        .divide(BigDecimal.valueOf(content.quantity()), 10, RoundingMode.HALF_EVEN);
                landed = purchaseCost.plus(new Money(shippingPerPiece, allocated.currency()));
            }
            return new ShipmentItem(content.itemId(), content.quantity(), purchaseCost, allocated, landed);
        }).toList();
        return new Shipment(row.id(), row.publicId(), row.fromLocationId(), row.toLocationId(), row.carrier(),
                row.trackingNumber(), Money.of(row.costAmount().toPlainString(), row.costCurrency()),
                row.allocationMethod(), row.status(), row.shippedDate(), row.arrivedDate(), items);
    }

    private static void requireNoDuplicates(List<String> units, List<String> lots) {
        Set<String> seen = new HashSet<>();
        for (String id : units) {
            if (!seen.add(id)) {
                throw new BusinessRuleException(id + " is listed twice");
            }
        }
        for (String id : lots) {
            if (!seen.add(id)) {
                throw new BusinessRuleException(id + " is listed twice");
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
