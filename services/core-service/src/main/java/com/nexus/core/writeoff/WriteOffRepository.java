package com.nexus.core.writeoff;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes write-offs with plain SQL. */
@Repository
class WriteOffRepository {

    private final JdbcClient jdbc;

    WriteOffRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    String insert(Long unitId, Long lotId, long productId, int quantity, String reason, LocalDate date,
                  List<Money> cost) {
        record Inserted(long id, String publicId) {
        }
        Inserted row = jdbc.sql("""
                        INSERT INTO stock_write_offs (unit_id, lot_id, product_id, quantity, reason, write_off_date)
                        VALUES (:unitId, :lotId, :productId, :quantity, :reason, :date)
                        RETURNING id, public_id
                        """)
                .param("unitId", unitId)
                .param("lotId", lotId)
                .param("productId", productId)
                .param("quantity", quantity)
                .param("reason", reason)
                .param("date", date)
                .query((rs, n) -> new Inserted(rs.getLong("id"), rs.getString("public_id")))
                .single();
        for (Money part : cost) {
            jdbc.sql("INSERT INTO stock_write_off_costs (write_off_id, currency, amount) VALUES (:id, :currency, :amount)")
                    .param("id", row.id())
                    .param("currency", part.currency().getCurrencyCode())
                    .param("amount", part.amount())
                    .update();
        }
        return row.publicId();
    }

    /** The goods came back from the trash: their loss stops counting. */
    void cancelForUnit(long unitId) {
        jdbc.sql("UPDATE stock_write_offs SET cancelled_at = now() WHERE unit_id = :id AND cancelled_at IS NULL")
                .param("id", unitId)
                .update();
    }

    void cancelForLot(long lotId) {
        jdbc.sql("UPDATE stock_write_offs SET cancelled_at = now() WHERE lot_id = :id AND cancelled_at IS NULL")
                .param("id", lotId)
                .update();
    }

    /** Write-offs that still count, newest first. */
    List<WriteOff> findActive() {
        record Row(long id, String publicId, String unitId, String lotId, long productId, int quantity,
                   String reason, LocalDate date, String currency, BigDecimal amount) {
        }
        List<Row> rows = jdbc.sql("""
                        SELECT w.id, w.public_id, u.public_id AS unit_public_id, l.public_id AS lot_public_id,
                               w.product_id, w.quantity, w.reason, w.write_off_date, c.currency, c.amount
                        FROM stock_write_offs w
                        LEFT JOIN inventory_units u ON u.id = w.unit_id
                        LEFT JOIN inventory_lots l ON l.id = w.lot_id
                        LEFT JOIN stock_write_off_costs c ON c.write_off_id = w.id
                        WHERE w.cancelled_at IS NULL
                        ORDER BY w.write_off_date DESC, w.id DESC, c.currency
                        """)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("public_id"),
                        rs.getString("unit_public_id"), rs.getString("lot_public_id"), rs.getLong("product_id"),
                        rs.getInt("quantity"), rs.getString("reason"), rs.getObject("write_off_date", LocalDate.class),
                        rs.getString("currency"), rs.getBigDecimal("amount")))
                .list();
        Map<Long, List<Row>> byWriteOff = new LinkedHashMap<>();
        for (Row row : rows) {
            byWriteOff.computeIfAbsent(row.id(), id -> new ArrayList<>()).add(row);
        }
        return byWriteOff.values().stream().map(group -> {
            Row first = group.get(0);
            List<Money> cost = group.stream()
                    .filter(r -> r.currency() != null)
                    .map(r -> Money.of(r.amount().toPlainString(), r.currency()))
                    .toList();
            return new WriteOff(first.publicId(), first.unitId(), first.lotId(), first.productId(), first.quantity(),
                    first.reason(), first.date(), cost);
        }).toList();
    }
}
