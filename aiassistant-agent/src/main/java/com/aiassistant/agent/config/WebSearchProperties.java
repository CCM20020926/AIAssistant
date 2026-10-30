package com.aiassistant.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.websearch")
public class WebSearchProperties {

    private boolean enabled = true;
    private String apiKey;
    private String searchDepth = "basic";   // basic | advanced
    private int maxResults = 5;
    private boolean includeAnswer = true;
}
