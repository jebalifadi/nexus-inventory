package com.nexus.core.shipment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes shipments with plain SQL. */
@Repository
class ShipmentRepository {

    record ShipmentRow(long id, String publicId, long fromLocationId, long toLocationId, String carrier,
                       String trackingNumber, BigDecimal costAmount, String costCurrency,
                       AllocationMethod allocationMethod, ShipmentStatus status, LocalDate shippedDate,
                       LocalDate arrivedDate) {
    }

    record ContentRow(Long unitId, Long lotId, String itemId, int quantity, BigDecimal allocatedCost,
                      BigDecimal purchaseCostAmount, String purchaseCostCurrency) {
    }

    private static final String SHIPMENT_COLUMNS = "id, public_id, from_location_id, to_location_id, carrier, "
            + "tracking_number, cost_amount, cost_currency, allocation_method, status, shipped_date, arrived_date";

    private final JdbcClient jdbc;

    ShipmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insertShipment(long fromLocationId, long toLocationId, String carrier, String trackingNumber,
                        BigDecimal costAmount, String costCurrency, AllocationMethod method, LocalDate shippedDate) {
        return jdbc.sql("""
                        INSERT INTO shipments (from_location_id, to_location_id, carrier, tracking_number,
                                               cost_amount, cost_currency, allocation_method, status, shipped_date)
                        VALUES (:from, :to, :carrier, :tracking, :amount, :currency, :method, 'IN_TRANSIT',
                                :shippedDate)
                        RETURNING id""")
                .param("from", fromLocationId)
                .param("to", toLocationId)
                .param("carrier", carrier)
                .param("tracking", trackingNumber)
                .param("amount", costAmount)
                .param("currency", costCurrency)
                .param("method", method.name())
                .param("shippedDate", shippedDate)
                .query(Long.class)
                .single();
    }

    void insertContent(long shipmentId, Long unitId, Long lotId, int quantity, BigDecimal allocatedCost) {
        jdbc.sql("""
                        INSERT INTO shipment_contents (shipment_id, unit_id, lot_id, quantity, allocated_cost_amount)
                        VALUES (:shipmentId, :unitId, :lotId, :quantity, :allocated)""")
                .param("shipmentId", shipmentId)
                .param("unitId", unitId)
                .param("lotId", lotId)
                .param("quantity", quantity)
                .param("allocated", allocatedCost)
                .update();
    }

    void markArrived(long shipmentId, LocalDate arrivedDate) {
        jdbc.sql("UPDATE shipments SET status = 'ARRIVED', arrived_date = :arrivedDate WHERE id = :id")
                .param("arrivedDate", arrivedDate)
                .param("id", shipmentId)
                .update();
    }

    Optional<ShipmentRow> findById(long id) {
        return jdbc.sql("SELECT " + SHIPMENT_COLUMNS + " FROM shipments WHERE id = :value")
                .param("value", id)
                .query((rs, n) -> mapShipment(rs))
                .optional();
    }

    Optional<ShipmentRow> findByPublicId(String publicId) {
        return jdbc.sql("SELECT " + SHIPMENT_COLUMNS + " FROM shipments WHERE public_id = :value")
                .param("value", publicId)
                .query((rs, n) -> mapShipment(rs))
                .optional();
    }

    /** Locks the shipment row until the transaction ends, so it cannot arrive twice at the same time. */
    Optional<ShipmentRow> lockByPublicId(String publicId) {
        return jdbc.sql("SELECT " + SHIPMENT_COLUMNS + " FROM shipments WHERE public_id = :value FOR UPDATE")
                .param("value", publicId)
                .query((rs, n) -> mapShipment(rs))
                .optional();
    }

    List<ShipmentRow> findAll() {
        return jdbc.sql("SELECT " + SHIPMENT_COLUMNS + " FROM shipments ORDER BY shipped_date DESC, id DESC")
                .query((rs, n) -> mapShipment(rs))
                .list();
    }

    List<ContentRow> findContents(long shipmentId) {
        return jdbc.sql("""
                        SELECT c.unit_id, c.lot_id, c.quantity, c.allocated_cost_amount,
                               COALESCE(u.public_id, l.public_id) AS item_id,
                               COALESCE(u.unit_cost_amount, l.unit_cost_amount) AS cost_amount,
                               COALESCE(u.unit_cost_currency, l.unit_cost_currency) AS cost_currency
                        FROM shipment_contents c
                        LEFT JOIN inventory_units u ON u.id = c.unit_id
                        LEFT JOIN inventory_lots l ON l.id = c.lot_id
                        WHERE c.shipment_id = :shipmentId
                        ORDER BY c.id""")
                .param("shipmentId", shipmentId)
                .query((rs, n) -> new ContentRow(
                        rs.getObject("unit_id", Long.class),
                        rs.getObject("lot_id", Long.class),
                        rs.getString("item_id"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("allocated_cost_amount"),
                        rs.getBigDecimal("cost_amount"),
                        rs.getString("cost_currency").trim()))
                .list();
    }

    /** The shipping shares a unit or lot received, with how many pieces each share covered. */
    record ShippingShare(BigDecimal allocated, int quantity, String currency) {
    }

    List<ShippingShare> findSharesOfUnit(long unitId) {
        return findShares("c.unit_id", unitId);
    }

    List<ShippingShare> findSharesOfLot(long lotId) {
        return findShares("c.lot_id", lotId);
    }

    private List<ShippingShare> findShares(String column, long id) {
        return jdbc.sql("SELECT c.allocated_cost_amount, c.quantity, s.cost_currency FROM shipment_contents c "
                        + "JOIN shipments s ON s.id = c.shipment_id WHERE " + column + " = :id ORDER BY c.id")
                .param("id", id)
                .query((rs, n) -> new ShippingShare(rs.getBigDecimal("allocated_cost_amount"), rs.getInt("quantity"),
                        rs.getString("cost_currency").trim()))
                .list();
    }

    private static ShipmentRow mapShipment(ResultSet rs) throws SQLException {
        return new ShipmentRow(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getLong("from_location_id"),
                rs.getLong("to_location_id"),
                rs.getString("carrier"),
                rs.getString("tracking_number"),
                rs.getBigDecimal("cost_amount"),
                rs.getString("cost_currency").trim(),
                AllocationMethod.valueOf(rs.getString("allocation_method")),
                ShipmentStatus.valueOf(rs.getString("status")),
                rs.getObject("shipped_date", LocalDate.class),
                rs.getObject("arrived_date", LocalDate.class));
    }
}
