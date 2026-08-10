package com.aitrainercrm.platform.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
