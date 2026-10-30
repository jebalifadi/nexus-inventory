package com.nexus.core.shipment;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/** Goods travelling from one location to another, with a carrier, a tracking number and a cost. */
public record Shipment(
        Long id,
        String publicId,
        Long fromLocationId,
        Long toLocationId,
        String carrier,
        String trackingNumber,
        Money cost,
        AllocationMethod allocationMethod,
        ShipmentStatus status,
        LocalDate shippedDate,
        LocalDate arrivedDate,
        List<ShipmentItem> items) {

    public Shipment {
        items = List.copyOf(items);
    }
}
