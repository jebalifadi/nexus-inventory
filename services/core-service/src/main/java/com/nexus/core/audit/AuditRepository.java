package com.nexus.core.audit;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Appends to and reads the audit log. There is no update or delete, and the database forbids both. */
@Repository
public class AuditRepository {

    private final JdbcClient jdbc;

    AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void append(String username, String method, String path, int status) {
        jdbc.sql("INSERT INTO audit_log (username, method, path, status) VALUES (:username, :method, :path, :status)")
                .param("username", username)
                .param("method", method)
                .param("path", path.length() > 500 ? path.substring(0, 500) : path)
                .param("status", status)
                .update();
    }

    public List<AuditEntry> latest(int limit) {
        return jdbc.sql("SELECT id, occurred_at, username, method, path, status FROM audit_log "
                        + "ORDER BY id DESC LIMIT :limit")
                .param("limit", limit)
                .query((rs, n) -> new AuditEntry(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("username"), rs.getString("method"), rs.getString("path"), rs.getInt("status")))
                .list();
    }
}
