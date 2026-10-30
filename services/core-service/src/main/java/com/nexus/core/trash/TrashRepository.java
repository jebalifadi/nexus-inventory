package com.nexus.core.trash;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Soft delete for every kind of record: marks it deleted, restores it, and lists the trash.
 * Table names come only from {@link TrashType}, never from user input.
 */
@Repository
public class TrashRepository {

    private static final int MAX_REASON_LENGTH = 255;

    private final JdbcClient jdbc;

    TrashRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void markDeleted(TrashType type, long id, String reason) {
        String cleanReason = reason == null || reason.isBlank() ? null : reason.strip();
        if (cleanReason != null && cleanReason.length() > MAX_REASON_LENGTH) {
            throw new BusinessRuleException("Reason is too long (maximum " + MAX_REASON_LENGTH + " characters)");
        }
        jdbc.sql("UPDATE " + type.table + " SET deleted_at = now(), delete_reason = :reason WHERE id = :id")
                .param("reason", cleanReason)
                .param("id", id)
                .update();
    }

    public void restore(TrashType type, long id) {
        jdbc.sql("UPDATE " + type.table + " SET deleted_at = NULL, delete_reason = NULL WHERE id = :id")
                .param("id", id)
                .update();
    }

    public boolean isDeleted(TrashType type, long id) {
        return deletedState(type, id).orElse(false);
    }

    /** Refuses to restore a record that does not exist or is not deleted. */
    public void requireDeleted(TrashType type, long id, String what) {
        boolean deleted = deletedState(type, id).orElseThrow(() -> new NotFoundException(what + " not found"));
        if (!deleted) {
            throw new BusinessRuleException(what + " is not deleted");
        }
    }

    /** A clear "not found" error that also says when the record exists but was deleted. */
    public NotFoundException notFound(TrashType type, long id, String what) {
        return new NotFoundException(isDeleted(type, id) ? what + " is deleted" : what + " not found");
    }

    /** Same as {@link #notFound(TrashType, long, String)}, for units and lots found by their public ID. */
    public NotFoundException notFoundByPublicId(TrashType type, String publicId, String what) {
        boolean deleted = jdbc.sql("SELECT count(*) FROM " + type.table
                        + " WHERE public_id = :publicId AND deleted_at IS NOT NULL")
                .param("publicId", publicId)
                .query(Long.class)
                .single() > 0;
        return new NotFoundException(deleted ? what + " is deleted" : what + " not found");
    }

    /** Everything in the trash, most recently deleted first. */
    public List<TrashItem> findAll() {
        List<TrashItem> items = new ArrayList<>();
        for (TrashType type : TrashType.values()) {
            items.addAll(jdbc.sql("SELECT id, " + type.nameColumn + " AS name, deleted_at, delete_reason FROM "
                            + type.table + " WHERE deleted_at IS NOT NULL")
                    .query((rs, n) -> new TrashItem(type, rs.getLong("id"), rs.getString("name"),
                            rs.getObject("deleted_at", OffsetDateTime.class), rs.getString("delete_reason")))
                    .list());
        }
        items.sort(Comparator.comparing(TrashItem::deletedAt).reversed());
        return items;
    }

    private Optional<Boolean> deletedState(TrashType type, long id) {
        return jdbc.sql("SELECT deleted_at IS NOT NULL FROM " + type.table + " WHERE id = :id")
                .param("id", id)
                .query(Boolean.class)
                .optional();
    }
}
