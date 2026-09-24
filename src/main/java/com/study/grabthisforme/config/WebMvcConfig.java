package com.study.grabthisforme.config;

import com.study.grabthisforme.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebMvcConfig(
        AuthInterceptor authInterceptor,
        @Value("${grabthisforme.cors.allowed-origins:*}") String allowedOrigins
    ) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                // Only credential entry points are anonymous; /api/auth/me requires an identity.
                "/api/auth/login", "/api/auth/login/",
                "/api/auth/register", "/api/auth/register/",
                "/api/auth/refresh", "/api/auth/refresh/",
                "/api/auth/password-reset/request", "/api/auth/password-reset/request/",
                "/api/auth/password-reset/confirm", "/api/auth/password-reset/confirm/",
                "/api/public/**"
            );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedOrigins)
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }
}
