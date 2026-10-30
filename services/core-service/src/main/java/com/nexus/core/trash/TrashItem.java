package com.nexus.core.trash;

import java.time.OffsetDateTime;

/** One deleted record, as shown in the trash. */
public record TrashItem(TrashType type, long id, String name, OffsetDateTime deletedAt, String reason) {
}
