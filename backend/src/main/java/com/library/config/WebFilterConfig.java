package com.library.config;

import com.library.common.error.ApiErrorWriter;
import com.library.common.web.RequestLoggingFilter;
import com.library.common.web.RequiredHeaderFilter;
import com.library.common.web.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the two request filters with an explicit order, both before Spring MVC: logging first (so that requests
 * rejected by the header filter are logged too), then the Host/Origin/header checks.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class WebFilterConfig {

    static final int LOGGING_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    static final int HEADER_CHECK_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setName("requestLoggingFilter");
        registration.setOrder(LOGGING_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequiredHeaderFilter> requiredHeaderFilter(
            SecurityProperties properties, ApiErrorWriter errorWriter) {
        FilterRegistrationBean<RequiredHeaderFilter> registration =
                new FilterRegistrationBean<>(new RequiredHeaderFilter(properties, errorWriter));
        registration.setName("requiredHeaderFilter");
        registration.setOrder(HEADER_CHECK_ORDER);
        return registration;
    }
}
