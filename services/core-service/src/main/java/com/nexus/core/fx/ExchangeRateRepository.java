package com.nexus.core.fx;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes exchange rates with plain SQL. */
@Repository
class ExchangeRateRepository {

    private static final String COLUMNS = "id, base, quote, rate, rate_date, source";

    private final JdbcClient jdbc;

    ExchangeRateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insert(String base, String quote, BigDecimal rate, LocalDate rateDate, String source) {
        return jdbc.sql("""
                        INSERT INTO exchange_rates (base, quote, rate, rate_date, source)
                        VALUES (:base, :quote, :rate, :rateDate, :source)
                        RETURNING id""")
                .param("base", base)
                .param("quote", quote)
                .param("rate", rate)
                .param("rateDate", rateDate)
                .param("source", source)
                .query(Long.class)
                .single();
    }

    Optional<ExchangeRate> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM exchange_rates WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    /** The most recent rate from base to quote on or before the date: the rate that was valid that day. */
    Optional<ExchangeRate> findValidOn(String base, String quote, LocalDate date) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM exchange_rates "
                        + "WHERE base = :base AND quote = :quote AND rate_date <= :date "
                        + "ORDER BY rate_date DESC LIMIT 1")
                .param("base", base)
                .param("quote", quote)
                .param("date", date)
                .query((rs, n) -> map(rs))
                .optional();
    }

    List<ExchangeRate> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM exchange_rates ORDER BY rate_date DESC, base, quote")
                .query((rs, n) -> map(rs))
                .list();
    }

    private static ExchangeRate map(ResultSet rs) throws SQLException {
        return new ExchangeRate(rs.getLong("id"), rs.getString("base").trim(), rs.getString("quote").trim(),
                rs.getBigDecimal("rate"), rs.getObject("rate_date", LocalDate.class), rs.getString("source"));
    }
}
