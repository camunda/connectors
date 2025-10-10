package io.camunda.connector.agenticai.a2a.inbound.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.a2a.client.polling")
public record A2aClientPollingConfigurationProperties(
    @Positive @DefaultValue("10") int threadPoolSize) {}
