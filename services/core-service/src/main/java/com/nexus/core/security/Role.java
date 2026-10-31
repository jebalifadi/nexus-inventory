package com.nexus.core.security;

public enum Role {
    /** Full control: can create, change, delete and see everything. */
    OWNER,
    /** Can look at everything, change nothing. For an employee or an accountant. */
    VIEWER
}
