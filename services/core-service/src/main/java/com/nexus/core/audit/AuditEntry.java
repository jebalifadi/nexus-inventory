package com.nexus.core.audit;

import java.time.OffsetDateTime;

/** One recorded change: who, when, what and how it ended (HTTP status). */
public record AuditEntry(long id, OffsetDateTime occurredAt, String username, String method, String path,
                         int status) {
}
