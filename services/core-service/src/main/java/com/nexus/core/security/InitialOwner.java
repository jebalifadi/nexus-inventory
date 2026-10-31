package com.nexus.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the first OWNER at startup when nobody can log in yet, from configuration (never from code). */
@Component
class InitialOwner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialOwner.class);

    private final UserService users;
    private final String username;
    private final String password;

    InitialOwner(UserService users,
                 @Value("${nexus.security.initial-owner.username:}") String username,
                 @Value("${nexus.security.initial-owner.password:}") String password) {
        this.users = users;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.noUsers() && !username.isBlank() && !password.isBlank()) {
            users.createUser(username, password, Role.OWNER);
            log.info("Created the first owner account '{}'", username);
        }
    }
}
