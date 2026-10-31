package com.nexus.core.payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes payments with plain SQL. */
@Repository
class PaymentRepository {

    record PaymentRow(long id, String publicId, PaymentDirection direction, String reference, BigDecimal amount,
                      String currency, LocalDate paymentDate, PaymentMethod method, String notes,
                      PaymentKind kind) {
    }

    private static final String PAYMENT_QUERY = """
            SELECT p.id, p.public_id, p.direction, COALESCE(s.public_id, pu.public_id) AS reference,
                   p.amount, p.currency, p.payment_date, p.method, p.notes, p.kind
            FROM payments p
            LEFT JOIN sales s ON s.id = p.sale_id
            LEFT JOIN purchases pu ON pu.id = p.purchase_id
            """;

    private final JdbcClient jdbc;

    PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the sale until the transaction ends, so two payments cannot both pass the balance check. */
    Optional<Long> lockSale(String publicId) {
        return jdbc.sql("SELECT id FROM sales WHERE public_id = :publicId FOR UPDATE")
                .param("publicId", publicId)
                .query(Long.class)
                .optional();
    }

    Optional<Long> lockPurchase(String publicId) {
        return jdbc.sql("SELECT id FROM purchases WHERE public_id = :publicId FOR UPDATE")
                .param("publicId", publicId)
                .query(Long.class)
                .optional();
    }

    long insertPayment(PaymentDirection direction, Long saleId, Long purchaseId, BigDecimal amount, String currency,
                       LocalDate paymentDate, PaymentMethod method, String notes) {
        return jdbc.sql("""
                        INSERT INTO payments (direction, sale_id, purchase_id, amount, currency, payment_date,
                                              method, notes)
                        VALUES (:direction, :saleId, :purchaseId, :amount, :currency, :paymentDate, :method, :notes)
                        RETURNING id""")
                .param("direction", direction.name())
                .param("saleId", saleId)
                .param("purchaseId", purchaseId)
                .param("amount", amount)
                .param("currency", currency)
                .param("paymentDate", paymentDate)
                .param("method", method.name())
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    long insertRefund(long saleId, BigDecimal amount, String currency, LocalDate paymentDate, String notes) {
        return jdbc.sql("""
                        INSERT INTO payments (direction, kind, sale_id, amount, currency, payment_date, method, notes)
                        VALUES ('INCOMING', 'REFUND', :saleId, :amount, :currency, :paymentDate, 'OTHER', :notes)
                        RETURNING id""")
                .param("saleId", saleId)
                .param("amount", amount)
                .param("currency", currency)
                .param("paymentDate", paymentDate)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    /** What the customer paid for a sale, minus what was refunded. */
    BigDecimal sumForSale(long saleId) {
        return jdbc.sql("SELECT COALESCE(SUM(CASE WHEN kind = 'REFUND' THEN -amount ELSE amount END), 0) "
                        + "FROM payments WHERE sale_id = :id")
                .param("id", saleId)
                .query(BigDecimal.class)
                .single();
    }

    BigDecimal sumForPurchase(long purchaseId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE purchase_id = :id")
                .param("id", purchaseId)
                .query(BigDecimal.class)
                .single();
    }

    Optional<PaymentRow> findPayment(long id) {
        return jdbc.sql(PAYMENT_QUERY + "WHERE p.id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    List<PaymentRow> findBySale(long saleId) {
        return jdbc.sql(PAYMENT_QUERY + "WHERE p.sale_id = :id ORDER BY p.payment_date, p.id")
                .param("id", saleId)
                .query((rs, n) -> map(rs))
                .list();
    }

    List<PaymentRow> findByPurchase(long purchaseId) {
        return jdbc.sql(PAYMENT_QUERY + "WHERE p.purchase_id = :id ORDER BY p.payment_date, p.id")
                .param("id", purchaseId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private static PaymentRow map(ResultSet rs) throws SQLException {
        return new PaymentRow(
                rs.getLong("id"),
                rs.getString("public_id"),
                PaymentDirection.valueOf(rs.getString("direction")),
                rs.getString("reference"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getObject("payment_date", LocalDate.class),
                PaymentMethod.valueOf(rs.getString("method")),
                rs.getString("notes"),
                PaymentKind.valueOf(rs.getString("kind")));
    }
}
