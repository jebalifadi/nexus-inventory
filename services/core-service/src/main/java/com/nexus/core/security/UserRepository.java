package com.nexus.core.security;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes users with plain SQL. */
@Repository
class UserRepository {

    record Credentials(User user, String passwordHash) {
    }

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long count() {
        return jdbc.sql("SELECT count(*) FROM users").query(Long.class).single();
    }

    long insert(String username, String passwordHash, Role role) {
        return jdbc.sql("INSERT INTO users (username, password_hash, role) VALUES (:username, :hash, :role) RETURNING id")
                .param("username", username)
                .param("hash", passwordHash)
                .param("role", role.name())
                .query(Long.class)
                .single();
    }

    Optional<Credentials> findCredentials(String username) {
        return jdbc.sql("SELECT id, username, role, active, password_hash FROM users WHERE username = :username")
                .param("username", username)
                .query((rs, n) -> new Credentials(new User(rs.getLong("id"), rs.getString("username"),
                        Role.valueOf(rs.getString("role")), rs.getBoolean("active")), rs.getString("password_hash")))
                .optional();
    }

    List<User> findAll() {
        return jdbc.sql("SELECT id, username, role, active FROM users ORDER BY username")
                .query((rs, n) -> new User(rs.getLong("id"), rs.getString("username"),
                        Role.valueOf(rs.getString("role")), rs.getBoolean("active")))
                .list();
    }
}
