package com.eltano.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.eltano.ecommerce.audit.api.AdminAuditInterceptor;
import com.eltano.ecommerce.catalog.config.ProductImageUploadProperties;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuditInterceptor adminAuditInterceptor;
    private final ProductImageUploadProperties productImageUploadProperties;

    public WebMvcConfig(AdminAuditInterceptor adminAuditInterceptor, ProductImageUploadProperties productImageUploadProperties) {
        this.adminAuditInterceptor = adminAuditInterceptor;
        this.productImageUploadProperties = productImageUploadProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuditInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(productImageUploadProperties.normalizedPublicPath() + "/**")
                .addResourceLocations(productImageUploadProperties.getDirectory().toAbsolutePath().normalize().toUri().toString() + "/");
    }
}
