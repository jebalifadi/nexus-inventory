package com.nexus.core.sale;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes customers and sales with plain SQL. */
@Repository
class SaleRepository {

    record SaleRow(long id, String publicId, Long customerId, String customerName, long locationId,
                   LocalDate saleDate, String currency, String notes) {
    }

    record LineRow(long id, int lineNo, long productId, String unitPublicId, int quantity, BigDecimal unitPrice,
                   BigDecimal costAmount, String costCurrency, int warrantyDays, String warrantyTerms,
                   Long unitId, BigDecimal unitCostAmount, String unitCostCurrency) {
    }

    record AllocationRow(String lotPublicId, int quantity, BigDecimal unitCostAmount, String unitCostCurrency,
                         long lotId, BigDecimal lotCostAmount, String lotCostCurrency) {
    }

    private static final String CUSTOMER_COLUMNS = "id, name, phone, email, country, notes";
    private static final String SALE_QUERY = """
            SELECT s.id, s.public_id, s.customer_id, c.name AS customer_name, s.location_id, s.sale_date,
                   s.currency, s.notes
            FROM sales s
            LEFT JOIN customers c ON c.id = s.customer_id
            """;

    private final JdbcClient jdbc;

    SaleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insertCustomer(String name, String phone, String email, String country, String notes) {
        return jdbc.sql("""
                        INSERT INTO customers (name, phone, email, country, notes)
                        VALUES (:name, :phone, :email, :country, :notes)
                        RETURNING id""")
                .param("name", name)
                .param("phone", phone)
                .param("email", email)
                .param("country", country)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    Optional<Customer> findCustomer(long id) {
        return jdbc.sql("SELECT " + CUSTOMER_COLUMNS + " FROM customers WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> mapCustomer(rs))
                .optional();
    }

    List<Customer> findCustomers() {
        return jdbc.sql("SELECT " + CUSTOMER_COLUMNS + " FROM customers ORDER BY name")
                .query((rs, n) -> mapCustomer(rs))
                .list();
    }

    long insertSale(Long customerId, long locationId, LocalDate saleDate, String currency, String notes) {
        return jdbc.sql("""
                        INSERT INTO sales (customer_id, location_id, sale_date, currency, notes)
                        VALUES (:customerId, :locationId, :saleDate, :currency, :notes)
                        RETURNING id""")
                .param("customerId", customerId)
                .param("locationId", locationId)
                .param("saleDate", saleDate)
                .param("currency", currency)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    long insertLine(long saleId, int lineNo, long productId, Long unitId, int quantity, BigDecimal unitPrice,
                    BigDecimal costAmount, String costCurrency, int warrantyDays, String warrantyTerms) {
        return jdbc.sql("""
                        INSERT INTO sale_lines (sale_id, line_no, product_id, unit_id, quantity, unit_price_amount,
                                                cost_amount, cost_currency, warranty_days, warranty_terms)
                        VALUES (:saleId, :lineNo, :productId, :unitId, :quantity, :unitPrice,
                                :costAmount, :costCurrency, :warrantyDays, :warrantyTerms)
                        RETURNING id""")
                .param("saleId", saleId)
                .param("lineNo", lineNo)
                .param("productId", productId)
                .param("unitId", unitId)
                .param("quantity", quantity)
                .param("unitPrice", unitPrice)
                .param("costAmount", costAmount)
                .param("costCurrency", costCurrency)
                .param("warrantyDays", warrantyDays)
                .param("warrantyTerms", warrantyTerms)
                .query(Long.class)
                .single();
    }

    void insertAllocation(long saleLineId, long lotId, int quantity, BigDecimal unitCostAmount,
                          String unitCostCurrency) {
        jdbc.sql("""
                        INSERT INTO stock_allocations
                            (sale_line_id, lot_id, quantity, unit_cost_amount, unit_cost_currency)
                        VALUES (:saleLineId, :lotId, :quantity, :amount, :currency)""")
                .param("saleLineId", saleLineId)
                .param("lotId", lotId)
                .param("quantity", quantity)
                .param("amount", unitCostAmount)
                .param("currency", unitCostCurrency)
                .update();
    }

    Optional<SaleRow> findSale(long id) {
        return jdbc.sql(SALE_QUERY + "WHERE s.id = :value")
                .param("value", id)
                .query((rs, n) -> mapSale(rs))
                .optional();
    }

    Optional<SaleRow> findSaleByPublicId(String publicId) {
        return jdbc.sql(SALE_QUERY + "WHERE s.public_id = :value")
                .param("value", publicId)
                .query((rs, n) -> mapSale(rs))
                .optional();
    }

    List<SaleRow> findSales() {
        return jdbc.sql(SALE_QUERY + "ORDER BY s.sale_date DESC, s.id DESC")
                .query((rs, n) -> mapSale(rs))
                .list();
    }

    List<LineRow> findLines(long saleId) {
        return jdbc.sql("""
                        SELECT l.id, l.line_no, l.product_id, u.public_id AS unit_public_id, l.quantity,
                               l.unit_price_amount, l.cost_amount, l.cost_currency, l.warranty_days, l.warranty_terms,
                               u.id AS unit_id, u.unit_cost_amount, u.unit_cost_currency
                        FROM sale_lines l
                        LEFT JOIN inventory_units u ON u.id = l.unit_id
                        WHERE l.sale_id = :saleId
                        ORDER BY l.line_no""")
                .param("saleId", saleId)
                .query((rs, n) -> new LineRow(
                        rs.getLong("id"),
                        rs.getInt("line_no"),
                        rs.getLong("product_id"),
                        rs.getString("unit_public_id"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price_amount"),
                        rs.getBigDecimal("cost_amount"),
                        trim(rs.getString("cost_currency")),
                        rs.getInt("warranty_days"),
                        rs.getString("warranty_terms"),
                        rs.getObject("unit_id", Long.class),
                        rs.getBigDecimal("unit_cost_amount"),
                        trim(rs.getString("unit_cost_currency"))))
                .list();
    }

    List<AllocationRow> findAllocations(long saleLineId) {
        return jdbc.sql("""
                        SELECT l.public_id, a.quantity, a.unit_cost_amount, a.unit_cost_currency,
                               l.id AS lot_id, l.unit_cost_amount AS lot_cost_amount,
                               l.unit_cost_currency AS lot_cost_currency
                        FROM stock_allocations a
                        JOIN inventory_lots l ON l.id = a.lot_id
                        WHERE a.sale_line_id = :saleLineId
                        ORDER BY a.id""")
                .param("saleLineId", saleLineId)
                .query((rs, n) -> new AllocationRow(rs.getString("public_id"), rs.getInt("quantity"),
                        rs.getBigDecimal("unit_cost_amount"), trim(rs.getString("unit_cost_currency")),
                        rs.getLong("lot_id"), rs.getBigDecimal("lot_cost_amount"),
                        trim(rs.getString("lot_cost_currency"))))
                .list();
    }

    private static Customer mapCustomer(ResultSet rs) throws SQLException {
        return new Customer(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("email"),
                rs.getString("country"), rs.getString("notes"));
    }

    private static SaleRow mapSale(ResultSet rs) throws SQLException {
        return new SaleRow(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getObject("customer_id", Long.class),
                rs.getString("customer_name"),
                rs.getLong("location_id"),
                rs.getObject("sale_date", LocalDate.class),
                rs.getString("currency").trim(),
                rs.getString("notes"));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
