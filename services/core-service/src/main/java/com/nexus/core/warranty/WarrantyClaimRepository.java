package com.nexus.core.warranty;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes warranty claims with plain SQL. */
@Repository
class WarrantyClaimRepository {

    /** A claim as stored, with the sale and line it is about. */
    record ClaimRow(long id, String publicId, String salePublicId, String saleCurrency, LocalDate saleDate,
                    int lineNo, int warrantyDays, String unitPublicId, int quantity, LocalDate claimDate, String issue,
                    boolean withinWarranty, String goodwillReason, ClaimStatus status, ClaimResolution resolution,
                    BigDecimal costAmount, String costCurrency, BigDecimal refundAmount, LocalDate resolvedDate,
                    String notes) {
    }

    private static final String QUERY = """
            SELECT c.id, c.public_id, s.public_id AS sale_public_id, s.currency AS sale_currency, s.sale_date,
                   l.line_no, l.warranty_days, u.public_id AS unit_public_id, c.quantity, c.claim_date, c.issue,
                   c.within_warranty, c.goodwill_reason, c.status, c.resolution, c.cost_amount, c.cost_currency,
                   c.refund_amount, c.resolved_date, c.notes
            FROM warranty_claims c
            JOIN sale_lines l ON l.id = c.sale_line_id
            JOIN sales s ON s.id = l.sale_id
            LEFT JOIN inventory_units u ON u.id = l.unit_id
            """;

    private final JdbcClient jdbc;

    WarrantyClaimRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insert(long saleLineId, int quantity, LocalDate claimDate, String issue, boolean withinWarranty,
                String goodwillReason) {
        return jdbc.sql("""
                        INSERT INTO warranty_claims (sale_line_id, quantity, claim_date, issue, within_warranty,
                                                     goodwill_reason, status)
                        VALUES (:saleLineId, :quantity, :claimDate, :issue, :within, :goodwill, 'OPEN')
                        RETURNING id""")
                .param("saleLineId", saleLineId)
                .param("quantity", quantity)
                .param("claimDate", claimDate)
                .param("issue", issue)
                .param("within", withinWarranty)
                .param("goodwill", goodwillReason)
                .query(Long.class)
                .single();
    }

    void close(long id, ClaimStatus status, ClaimResolution resolution, BigDecimal costAmount, String costCurrency,
               BigDecimal refundAmount, LocalDate resolvedDate, String notes) {
        jdbc.sql("""
                        UPDATE warranty_claims
                        SET status = :status, resolution = :resolution, cost_amount = :costAmount,
                            cost_currency = :costCurrency, refund_amount = :refundAmount,
                            resolved_date = :resolvedDate, notes = :notes
                        WHERE id = :id""")
                .param("status", status.name())
                .param("resolution", resolution.name())
                .param("costAmount", costAmount)
                .param("costCurrency", costCurrency)
                .param("refundAmount", refundAmount)
                .param("resolvedDate", resolvedDate)
                .param("notes", notes)
                .param("id", id)
                .update();
    }

    Optional<ClaimRow> findById(long id) {
        return jdbc.sql(QUERY + "WHERE c.id = :value")
                .param("value", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    Optional<ClaimRow> findByPublicId(String publicId) {
        return jdbc.sql(QUERY + "WHERE c.public_id = :value")
                .param("value", publicId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    /** Locks the claim until the transaction ends, so it cannot be resolved twice at the same time. */
    Optional<Long> lock(String publicId) {
        return jdbc.sql("SELECT id FROM warranty_claims WHERE public_id = :publicId FOR UPDATE")
                .param("publicId", publicId)
                .query(Long.class)
                .optional();
    }

    List<ClaimRow> findAll() {
        return jdbc.sql(QUERY + "ORDER BY c.claim_date DESC, c.id DESC")
                .query((rs, n) -> map(rs))
                .list();
    }

    private static ClaimRow map(ResultSet rs) throws SQLException {
        String resolution = rs.getString("resolution");
        String costCurrency = rs.getString("cost_currency");
        return new ClaimRow(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getString("sale_public_id"),
                rs.getString("sale_currency").trim(),
                rs.getObject("sale_date", LocalDate.class),
                rs.getInt("line_no"),
                rs.getInt("warranty_days"),
                rs.getString("unit_public_id"),
                rs.getInt("quantity"),
                rs.getObject("claim_date", LocalDate.class),
                rs.getString("issue"),
                rs.getBoolean("within_warranty"),
                rs.getString("goodwill_reason"),
                ClaimStatus.valueOf(rs.getString("status")),
                resolution == null ? null : ClaimResolution.valueOf(resolution),
                rs.getBigDecimal("cost_amount"),
                costCurrency == null ? null : costCurrency.trim(),
                rs.getBigDecimal("refund_amount"),
                rs.getObject("resolved_date", LocalDate.class),
                rs.getString("notes"));
    }
}
