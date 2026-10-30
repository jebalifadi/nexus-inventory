package com.nexus.core.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes suppliers and purchases with plain SQL. */
@Repository
class PurchaseRepository {

    /** A purchase line as stored, before the stock it created is added. */
    record LineRow(long id, int lineNo, long productId, long locationId, int quantity, BigDecimal unitPrice) {
    }

    /** A purchase header as stored, with its supplier's name. */
    record PurchaseRow(long id, String publicId, long supplierId, String supplierName, LocalDate purchaseDate,
                       String currency, String notes) {
    }

    private static final String SUPPLIER_COLUMNS = "id, name, country, contact, notes";
    private static final String PURCHASE_QUERY = """
            SELECT p.id, p.public_id, p.supplier_id, s.name AS supplier_name, p.purchase_date, p.currency, p.notes
            FROM purchases p
            JOIN suppliers s ON s.id = p.supplier_id
            """;

    private final JdbcClient jdbc;

    PurchaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insertSupplier(String name, String country, String contact, String notes) {
        return jdbc.sql("""
                        INSERT INTO suppliers (name, country, contact, notes)
                        VALUES (:name, :country, :contact, :notes)
                        RETURNING id""")
                .param("name", name)
                .param("country", country)
                .param("contact", contact)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    Optional<Supplier> findSupplier(long id) {
        return jdbc.sql("SELECT " + SUPPLIER_COLUMNS + " FROM suppliers WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Supplier(rs.getLong("id"), rs.getString("name"), rs.getString("country"),
                        rs.getString("contact"), rs.getString("notes")))
                .optional();
    }

    List<Supplier> findSuppliers() {
        return jdbc.sql("SELECT " + SUPPLIER_COLUMNS + " FROM suppliers ORDER BY name")
                .query((rs, n) -> new Supplier(rs.getLong("id"), rs.getString("name"), rs.getString("country"),
                        rs.getString("contact"), rs.getString("notes")))
                .list();
    }

    long insertPurchase(long supplierId, LocalDate purchaseDate, String currency, String notes) {
        return jdbc.sql("""
                        INSERT INTO purchases (supplier_id, purchase_date, currency, notes)
                        VALUES (:supplierId, :purchaseDate, :currency, :notes)
                        RETURNING id""")
                .param("supplierId", supplierId)
                .param("purchaseDate", purchaseDate)
                .param("currency", currency)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    long insertLine(long purchaseId, int lineNo, long productId, long locationId, int quantity,
                    BigDecimal unitPrice) {
        return jdbc.sql("""
                        INSERT INTO purchase_lines
                            (purchase_id, line_no, product_id, location_id, quantity, unit_price_amount)
                        VALUES (:purchaseId, :lineNo, :productId, :locationId, :quantity, :unitPrice)
                        RETURNING id""")
                .param("purchaseId", purchaseId)
                .param("lineNo", lineNo)
                .param("productId", productId)
                .param("locationId", locationId)
                .param("quantity", quantity)
                .param("unitPrice", unitPrice)
                .query(Long.class)
                .single();
    }

    Optional<PurchaseRow> findPurchase(long id) {
        return jdbc.sql(PURCHASE_QUERY + "WHERE p.id = :value")
                .param("value", id)
                .query((rs, n) -> mapPurchase(rs))
                .optional();
    }

    Optional<PurchaseRow> findPurchaseByPublicId(String publicId) {
        return jdbc.sql(PURCHASE_QUERY + "WHERE p.public_id = :value")
                .param("value", publicId)
                .query((rs, n) -> mapPurchase(rs))
                .optional();
    }

    List<PurchaseRow> findPurchases() {
        return jdbc.sql(PURCHASE_QUERY + "ORDER BY p.purchase_date DESC, p.id DESC")
                .query((rs, n) -> mapPurchase(rs))
                .list();
    }

    List<LineRow> findLines(long purchaseId) {
        return jdbc.sql("""
                        SELECT id, line_no, product_id, location_id, quantity, unit_price_amount
                        FROM purchase_lines
                        WHERE purchase_id = :purchaseId
                        ORDER BY line_no""")
                .param("purchaseId", purchaseId)
                .query((rs, n) -> new LineRow(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("product_id"),
                        rs.getLong("location_id"), rs.getInt("quantity"), rs.getBigDecimal("unit_price_amount")))
                .list();
    }

    private static PurchaseRow mapPurchase(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PurchaseRow(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getLong("supplier_id"),
                rs.getString("supplier_name"),
                rs.getObject("purchase_date", LocalDate.class),
                rs.getString("currency").trim(),
                rs.getString("notes"));
    }
}
