package za.co.capitecbank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fraud-rules")
public record RuleEngineConfig(
        boolean rule001Enabled,
        boolean rule002Enabled,
        boolean rule003Enabled,
        boolean rule004Enabled,
        boolean rule005Enabled,
        boolean rule006Enabled) {}
