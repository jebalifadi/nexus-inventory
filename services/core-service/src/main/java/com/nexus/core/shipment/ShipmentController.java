package com.nexus.core.shipment;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.web.MoneyJson;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for shipments. */
@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentController {

    record NewShipment(Long fromLocationId, Long toLocationId, String carrier, String trackingNumber,
                       MoneyJson cost, AllocationMethod allocationMethod, LocalDate shippedDate,
                       List<String> unitIds, List<String> lotIds) {
    }

    record Arrival(LocalDate arrivedDate) {
    }

    record ItemView(String id, int quantity, MoneyJson purchaseCost, MoneyJson shippingCost,
                    MoneyJson landedUnitCost) {

        static ItemView of(ShipmentItem item) {
            return new ItemView(item.itemId(), item.quantity(), MoneyJson.of(item.purchaseCost()),
                    MoneyJson.of(item.allocatedCost()), MoneyJson.of(item.landedUnitCost()));
        }
    }

    record ShipmentView(String id, Long fromLocationId, Long toLocationId, String carrier, String trackingNumber,
                        MoneyJson cost, AllocationMethod allocationMethod, ShipmentStatus status,
                        LocalDate shippedDate, LocalDate arrivedDate, List<ItemView> items) {

        static ShipmentView of(Shipment s) {
            return new ShipmentView(s.publicId(), s.fromLocationId(), s.toLocationId(), s.carrier(),
                    s.trackingNumber(), MoneyJson.of(s.cost()), s.allocationMethod(), s.status(),
                    s.shippedDate(), s.arrivedDate(), s.items().stream().map(ItemView::of).toList());
        }
    }

    private final ShipmentService shipments;

    ShipmentController(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ShipmentView create(@RequestBody NewShipment request) {
        if (request.fromLocationId() == null || request.toLocationId() == null) {
            throw new BusinessRuleException("fromLocationId and toLocationId are required");
        }
        if (request.cost() == null) {
            throw new BusinessRuleException("cost is required (use 0 for free shipping)");
        }
        return ShipmentView.of(shipments.createShipment(request.fromLocationId(), request.toLocationId(),
                request.carrier(), request.trackingNumber(), request.cost().toMoney(), request.allocationMethod(),
                request.shippedDate(), request.unitIds(), request.lotIds()));
    }

    @GetMapping
    List<ShipmentView> list() {
        return shipments.listShipments().stream().map(ShipmentView::of).toList();
    }

    @GetMapping("/{id}")
    ShipmentView get(@PathVariable("id") String id) {
        return ShipmentView.of(shipments.getShipment(id));
    }

    @PostMapping("/{id}/arrive")
    ShipmentView arrive(@PathVariable("id") String id, @RequestBody(required = false) Arrival request) {
        return ShipmentView.of(shipments.markArrived(id, request == null ? null : request.arrivedDate()));
    }
}
