package com.nexus.core.cash;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes capital movements and currency exchanges, and calculates cash from every movement. */
@Repository
class CashRepository {

    /**
     * Every movement of money, signed: + in, − out. Customer payments come in; refunds, supplier
     * payments and expenses go out; capital and exchanges go both ways.
     */
    private static final String MOVEMENTS = """
            SELECT m.date, m.type, m.reference, m.currency, m.amount FROM (
                SELECT p.payment_date AS date,
                       CASE WHEN p.kind = 'REFUND' THEN 'REFUND'
                            WHEN p.direction = 'INCOMING' THEN 'SALE_PAYMENT'
                            ELSE 'SUPPLIER_PAYMENT' END AS type,
                       COALESCE(s.public_id, pu.public_id) AS reference,
                       p.currency,
                       CASE WHEN p.direction = 'INCOMING' AND p.kind = 'PAYMENT' THEN p.amount ELSE -p.amount END AS amount,
                       p.id AS sort_id
                FROM payments p
                LEFT JOIN sales s ON s.id = p.sale_id
                LEFT JOIN purchases pu ON pu.id = p.purchase_id
                UNION ALL
                SELECT expense_date, 'EXPENSE', public_id, currency, -amount, id FROM expenses
                UNION ALL
                SELECT movement_date, type, public_id, currency,
                       CASE WHEN type IN ('OPENING_BALANCE', 'CAPITAL_ADDED') THEN amount ELSE -amount END, id
                FROM capital_movements
                UNION ALL
                SELECT exchange_date, 'EXCHANGE_OUT', public_id, from_currency, -from_amount, id FROM currency_exchanges
                UNION ALL
                SELECT exchange_date, 'EXCHANGE_IN', public_id, to_currency, to_amount, id FROM currency_exchanges
            ) m
            """;

    private final JdbcClient jdbc;

    CashRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Only one cash change at a time, so two withdrawals cannot both pass the "enough cash?" check. */
    void lockCash() {
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(7311)) AS cash_lock").query(Integer.class).single();
    }

    Map<String, BigDecimal> cashByCurrency() {
        Map<String, BigDecimal> cash = new LinkedHashMap<>();
        jdbc.sql("SELECT currency, SUM(amount) AS total FROM (" + MOVEMENTS + ") c GROUP BY currency ORDER BY currency")
                .query((rs, n) -> Map.entry(rs.getString("currency").trim(), rs.getBigDecimal("total")))
                .list()
                .forEach(e -> cash.put(e.getKey(), e.getValue()));
        return cash;
    }

    List<CashMovement> movements() {
        return jdbc.sql(MOVEMENTS + "ORDER BY m.date, m.type, m.sort_id")
                .query((rs, n) -> new CashMovement(
                        rs.getObject("date", LocalDate.class),
                        rs.getString("type"),
                        rs.getString("reference"),
                        Money.of(rs.getBigDecimal("amount").toPlainString(), rs.getString("currency").trim())))
                .list();
    }

    long insertCapital(CapitalType type, BigDecimal amount, String currency, LocalDate date, String notes) {
        return jdbc.sql("""
                        INSERT INTO capital_movements (type, amount, currency, movement_date, notes)
                        VALUES (:type, :amount, :currency, :date, :notes)
                        RETURNING id""")
                .param("type", type.name())
                .param("amount", amount)
                .param("currency", currency)
                .param("date", date)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    Optional<CapitalMovement> findCapital(long id) {
        return jdbc.sql("SELECT id, public_id, type, amount, currency, movement_date, notes "
                        + "FROM capital_movements WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new CapitalMovement(rs.getLong("id"), rs.getString("public_id"),
                        CapitalType.valueOf(rs.getString("type")),
                        Money.of(rs.getBigDecimal("amount").toPlainString(), rs.getString("currency").trim()),
                        rs.getObject("movement_date", LocalDate.class), rs.getString("notes")))
                .optional();
    }

    /** Sums of capital movements per currency and type. */
    Map<String, Map<CapitalType, BigDecimal>> capitalTotals() {
        Map<String, Map<CapitalType, BigDecimal>> totals = new LinkedHashMap<>();
        jdbc.sql("SELECT currency, type, SUM(amount) AS total FROM capital_movements GROUP BY currency, type "
                        + "ORDER BY currency")
                .query((rs, n) -> new Object[] {rs.getString("currency").trim(), CapitalType.valueOf(rs.getString("type")),
                        rs.getBigDecimal("total")})
                .list()
                .forEach(row -> totals.computeIfAbsent((String) row[0], k -> new LinkedHashMap<>())
                        .put((CapitalType) row[1], (BigDecimal) row[2]));
        return totals;
    }

    long insertExchange(BigDecimal fromAmount, String fromCurrency, BigDecimal toAmount, String toCurrency,
                        LocalDate date, String notes) {
        return jdbc.sql("""
                        INSERT INTO currency_exchanges
                            (from_amount, from_currency, to_amount, to_currency, exchange_date, notes)
                        VALUES (:fromAmount, :fromCurrency, :toAmount, :toCurrency, :date, :notes)
                        RETURNING id""")
                .param("fromAmount", fromAmount)
                .param("fromCurrency", fromCurrency)
                .param("toAmount", toAmount)
                .param("toCurrency", toCurrency)
                .param("date", date)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    Optional<CurrencyExchange> findExchange(long id) {
        return jdbc.sql("SELECT id, public_id, from_amount, from_currency, to_amount, to_currency, exchange_date, notes "
                        + "FROM currency_exchanges WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new CurrencyExchange(rs.getLong("id"), rs.getString("public_id"),
                        Money.of(rs.getBigDecimal("from_amount").toPlainString(), rs.getString("from_currency").trim()),
                        Money.of(rs.getBigDecimal("to_amount").toPlainString(), rs.getString("to_currency").trim()),
                        rs.getObject("exchange_date", LocalDate.class), rs.getString("notes")))
                .optional();
    }
}
