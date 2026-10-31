package com.nexus.core.security;

/** Someone who can log in. The password is only ever stored as a hash. */
public record User(Long id, String username, Role role, boolean active) {
}
