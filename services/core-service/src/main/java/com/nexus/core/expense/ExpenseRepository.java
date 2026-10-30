package com.nexus.core.expense;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes expenses with plain SQL. */
@Repository
class ExpenseRepository {

    private static final String QUERY = """
            SELECT e.id, e.public_id, e.category, e.amount, e.currency, e.expense_date, e.description,
                   u.public_id AS unit_public_id, s.public_id AS sale_public_id
            FROM expenses e
            LEFT JOIN inventory_units u ON u.id = e.unit_id
            LEFT JOIN sales s ON s.id = e.sale_id
            """;

    private final JdbcClient jdbc;

    ExpenseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insert(ExpenseCategory category, BigDecimal amount, String currency, LocalDate date, String description,
                Long unitId, Long saleId) {
        return jdbc.sql("""
                        INSERT INTO expenses (category, amount, currency, expense_date, description, unit_id, sale_id)
                        VALUES (:category, :amount, :currency, :date, :description, :unitId, :saleId)
                        RETURNING id""")
                .param("category", category.name())
                .param("amount", amount)
                .param("currency", currency)
                .param("date", date)
                .param("description", description)
                .param("unitId", unitId)
                .param("saleId", saleId)
                .query(Long.class)
                .single();
    }

    Optional<Expense> findById(long id) {
        return jdbc.sql(QUERY + "WHERE e.id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    List<Expense> findAll() {
        return jdbc.sql(QUERY + "ORDER BY e.expense_date DESC, e.id DESC")
                .query((rs, n) -> map(rs))
                .list();
    }

    List<Money> amountsForUnit(long unitId) {
        return amounts("unit_id", unitId);
    }

    List<Money> amountsForSale(long saleId) {
        return amounts("sale_id", saleId);
    }

    private List<Money> amounts(String column, long id) {
        return jdbc.sql("SELECT amount, currency FROM expenses WHERE " + column + " = :id ORDER BY id")
                .param("id", id)
                .query((rs, n) -> Money.of(rs.getBigDecimal("amount").toPlainString(), rs.getString("currency").trim()))
                .list();
    }

    private static Expense map(ResultSet rs) throws SQLException {
        return new Expense(
                rs.getLong("id"),
                rs.getString("public_id"),
                ExpenseCategory.valueOf(rs.getString("category")),
                Money.of(rs.getBigDecimal("amount").toPlainString(), rs.getString("currency").trim()),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("description"),
                rs.getString("unit_public_id"),
                rs.getString("sale_public_id"));
    }
}
