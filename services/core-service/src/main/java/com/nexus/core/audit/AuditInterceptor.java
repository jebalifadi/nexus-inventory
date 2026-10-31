package com.nexus.core.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Records every request that changes something (anything but GET), with who made it and the
 * result, including failed logins. Request bodies are never stored, so passwords never reach the log.
 */
@Component
class AuditInterceptor implements HandlerInterceptor {

    private final AuditRepository audit;

    AuditInterceptor(AuditRepository audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return;
        }
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = user != null && user.isAuthenticated() && !(user instanceof AnonymousAuthenticationToken);
        String username = loggedIn ? user.getName() : "anonymous";
        audit.append(username, method, request.getRequestURI(), response.getStatus());
    }
}
