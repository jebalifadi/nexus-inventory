package com.nexus.core.shipment;

import com.nexus.core.money.Money;

/**
 * One unit or lot inside a shipment.
 *
 * @param itemId          the unit (U-…) or lot (LOT-…) ID
 * @param purchaseCost    what one piece cost to buy
 * @param allocatedCost   this item's share of the shipping cost (for a lot: all its pieces together)
 * @param landedUnitCost  real cost of one piece, purchase plus shipping; empty when the two are in
 *                        different currencies, until exchange rates exist
 */
public record ShipmentItem(String itemId, int quantity, Money purchaseCost, Money allocatedCost,
                           Money landedUnitCost) {
}
