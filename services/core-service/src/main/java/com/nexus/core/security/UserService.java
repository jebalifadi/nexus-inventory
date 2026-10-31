package com.nexus.core.security;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.UnauthorizedException;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Users and login. */
@Service
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 10;

    private final UserRepository repository;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    UserService(UserRepository repository, PasswordEncoder passwords, TokenService tokens) {
        this.repository = repository;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public User createUser(String username, String password, Role role) {
        if (username == null || username.isBlank()) {
            throw new BusinessRuleException("Username is required");
        }
        if (role == null) {
            throw new BusinessRuleException("Role is required (OWNER or VIEWER)");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("Password must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        long id = repository.insert(username.strip(), passwords.encode(password), role);
        return new User(id, username.strip(), role, true);
    }

    /** Checks the password and gives a token. The error never says which of the two was wrong. */
    @Transactional(readOnly = true)
    public TokenService.Token login(String username, String password) {
        UserRepository.Credentials credentials = repository.findCredentials(username == null ? "" : username.strip())
                .filter(c -> c.user().active())
                .filter(c -> password != null && passwords.matches(password, c.passwordHash()))
                .orElseThrow(() -> new UnauthorizedException("Wrong username or password"));
        return tokens.issue(credentials.user());
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return repository.findAll();
    }

    /** True when nobody can log in yet: used to create the first owner at startup. */
    @Transactional(readOnly = true)
    public boolean noUsers() {
        return repository.count() == 0;
    }
}
