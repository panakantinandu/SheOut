package com.sheout.staff.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the permission check in front of every endpoint. */
@Configuration
class StaffWebConfig implements WebMvcConfigurer {

    private final StaffPermissionInterceptor permissions;

    StaffWebConfig(StaffPermissionInterceptor permissions) {
        this.permissions = permissions;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissions);
    }
}
