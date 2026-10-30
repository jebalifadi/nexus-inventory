package com.nexus.core.stock;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes stock data with plain SQL. */
@Repository
class StockRepository {

    private static final String UNIT_COLUMNS =
            "id, public_id, product_id, location_id, status, unit_cost_amount, unit_cost_currency, received_date";
    private static final String LOT_COLUMNS = "id, public_id, product_id, location_id, quantity_received, "
            + "quantity_remaining, unit_cost_amount, unit_cost_currency, received_date, status";

    private final JdbcClient jdbc;

    StockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insertLocation(String name, String country) {
        return jdbc.sql("INSERT INTO locations (name, country) VALUES (:name, :country) RETURNING id")
                .param("name", name)
                .param("country", country)
                .query(Long.class)
                .single();
    }

    Optional<Location> findLocation(long id) {
        return jdbc.sql("SELECT id, name, country FROM locations WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Location(rs.getLong("id"), rs.getString("name"), rs.getString("country")))
                .optional();
    }

    List<Location> findLocations() {
        return jdbc.sql("SELECT id, name, country FROM locations ORDER BY name")
                .query((rs, n) -> new Location(rs.getLong("id"), rs.getString("name"), rs.getString("country")))
                .list();
    }

    long insertUnit(long productId, long locationId, UnitStatus status, Money cost, LocalDate receivedDate) {
        return jdbc.sql("""
                        INSERT INTO inventory_units
                            (product_id, location_id, status, unit_cost_amount, unit_cost_currency, received_date)
                        VALUES (:productId, :locationId, :status, :amount, :currency, :receivedDate)
                        RETURNING id""")
                .param("productId", productId)
                .param("locationId", locationId)
                .param("status", status.name())
                .param("amount", cost.amount())
                .param("currency", cost.currency().getCurrencyCode())
                .param("receivedDate", receivedDate)
                .query(Long.class)
                .single();
    }

    void insertUnitAttribute(long unitId, long fieldId, String value) {
        jdbc.sql("INSERT INTO unit_attributes (unit_id, field_id, value) VALUES (:unitId, :fieldId, :value)")
                .param("unitId", unitId)
                .param("fieldId", fieldId)
                .param("value", value)
                .update();
    }

    void insertStatusChange(long unitId, UnitStatus from, UnitStatus to) {
        jdbc.sql("INSERT INTO unit_status_history (unit_id, from_status, to_status) VALUES (:unitId, :from, :to)")
                .param("unitId", unitId)
                .param("from", from == null ? null : from.name())
                .param("to", to.name())
                .update();
    }

    void updateUnitStatus(long unitId, UnitStatus status) {
        jdbc.sql("UPDATE inventory_units SET status = :status WHERE id = :id")
                .param("status", status.name())
                .param("id", unitId)
                .update();
    }

    Optional<InventoryUnit> findUnitById(long id) {
        return findUnit("SELECT " + UNIT_COLUMNS + " FROM inventory_units WHERE id = :value", id);
    }

    Optional<InventoryUnit> findUnitByPublicId(String publicId) {
        return findUnit("SELECT " + UNIT_COLUMNS + " FROM inventory_units WHERE public_id = :value", publicId);
    }

    /** Locks the unit row until the transaction ends, so two requests cannot change it at once. */
    Optional<InventoryUnit> lockUnitByPublicId(String publicId) {
        return findUnit("SELECT " + UNIT_COLUMNS + " FROM inventory_units WHERE public_id = :value FOR UPDATE",
                publicId);
    }

    List<InventoryUnit> findUnits() {
        return jdbc.sql("SELECT " + UNIT_COLUMNS + " FROM inventory_units ORDER BY id")
                .query((rs, n) -> mapUnit(rs))
                .list()
                .stream()
                .map(this::withAttributes)
                .toList();
    }

    long insertLot(long productId, long locationId, int quantity, Money unitCost, LocalDate receivedDate) {
        return jdbc.sql("""
                        INSERT INTO inventory_lots (product_id, location_id, quantity_received, quantity_remaining,
                                                    unit_cost_amount, unit_cost_currency, received_date, status)
                        VALUES (:productId, :locationId, :quantity, :quantity, :amount, :currency,
                                :receivedDate, 'IN_STOCK')
                        RETURNING id""")
                .param("productId", productId)
                .param("locationId", locationId)
                .param("quantity", quantity)
                .param("amount", unitCost.amount())
                .param("currency", unitCost.currency().getCurrencyCode())
                .param("receivedDate", receivedDate)
                .query(Long.class)
                .single();
    }

    Optional<InventoryLot> findLotById(long id) {
        return jdbc.sql("SELECT " + LOT_COLUMNS + " FROM inventory_lots WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> mapLot(rs))
                .optional();
    }

    Optional<InventoryLot> findLotByPublicId(String publicId) {
        return jdbc.sql("SELECT " + LOT_COLUMNS + " FROM inventory_lots WHERE public_id = :publicId")
                .param("publicId", publicId)
                .query((rs, n) -> mapLot(rs))
                .optional();
    }

    List<InventoryLot> findLots() {
        return jdbc.sql("SELECT " + LOT_COLUMNS + " FROM inventory_lots ORDER BY id")
                .query((rs, n) -> mapLot(rs))
                .list();
    }

    List<StockLevel> findStockLevels() {
        return jdbc.sql("""
                        SELECT s.product_id, p.name AS product_name, s.location_id, l.name AS location_name,
                               SUM(s.quantity) AS quantity
                        FROM (
                            SELECT product_id, location_id, 1 AS quantity
                            FROM inventory_units
                            WHERE status IN ('IN_INVENTORY', 'LISTED', 'RESERVED')
                            UNION ALL
                            SELECT product_id, location_id, quantity_remaining
                            FROM inventory_lots
                            WHERE status = 'IN_STOCK'
                        ) s
                        JOIN products p ON p.id = s.product_id
                        JOIN locations l ON l.id = s.location_id
                        GROUP BY s.product_id, p.name, s.location_id, l.name
                        HAVING SUM(s.quantity) > 0
                        ORDER BY p.name, l.name""")
                .query((rs, n) -> new StockLevel(rs.getLong("product_id"), rs.getString("product_name"),
                        rs.getLong("location_id"), rs.getString("location_name"), rs.getLong("quantity")))
                .list();
    }

    private Optional<InventoryUnit> findUnit(String sql, Object value) {
        return jdbc.sql(sql)
                .param("value", value)
                .query((rs, n) -> mapUnit(rs))
                .optional()
                .map(this::withAttributes);
    }

    private InventoryUnit withAttributes(InventoryUnit unit) {
        Map<String, String> attributes = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT f.field_key, a.value
                        FROM unit_attributes a
                        JOIN category_fields f ON f.id = a.field_id
                        WHERE a.unit_id = :unitId
                        ORDER BY f.position""")
                .param("unitId", unit.id())
                .query((rs, n) -> Map.entry(rs.getString("field_key"), rs.getString("value")))
                .list()
                .forEach(e -> attributes.put(e.getKey(), e.getValue()));
        return new InventoryUnit(unit.id(), unit.publicId(), unit.productId(), unit.locationId(), unit.status(),
                unit.unitCost(), unit.receivedDate(), attributes);
    }

    private static InventoryUnit mapUnit(ResultSet rs) throws SQLException {
        return new InventoryUnit(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getLong("product_id"),
                rs.getLong("location_id"),
                UnitStatus.valueOf(rs.getString("status")),
                money(rs.getBigDecimal("unit_cost_amount"), rs.getString("unit_cost_currency")),
                rs.getObject("received_date", LocalDate.class),
                Map.of());
    }

    private static InventoryLot mapLot(ResultSet rs) throws SQLException {
        return new InventoryLot(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getLong("product_id"),
                rs.getLong("location_id"),
                rs.getInt("quantity_received"),
                rs.getInt("quantity_remaining"),
                money(rs.getBigDecimal("unit_cost_amount"), rs.getString("unit_cost_currency")),
                rs.getObject("received_date", LocalDate.class),
                rs.getString("status"));
    }

    private static Money money(BigDecimal amount, String currency) {
        return Money.of(amount.toPlainString(), currency.trim());
    }
}
