package com.eltano.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.eltano.ecommerce.audit.api.AdminAuditInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuditInterceptor adminAuditInterceptor;

    public WebMvcConfig(AdminAuditInterceptor adminAuditInterceptor) {
        this.adminAuditInterceptor = adminAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuditInterceptor);
    }
}
