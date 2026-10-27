package com.aiassistant.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.agent")
public class AgentProperties {

    private String modelDefault = "qwen3.5-flash";
    private String modelThinking = "qwen-plus";
}
