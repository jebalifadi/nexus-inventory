package com.nexus.core.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Plugs the audit interceptor into every API request. */
@Configuration
class AuditWebConfig implements WebMvcConfigurer {

    private final AuditInterceptor interceptor;

    AuditWebConfig(AuditInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
