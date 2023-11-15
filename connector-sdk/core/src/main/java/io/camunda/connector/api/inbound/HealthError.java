package io.camunda.connector.api.inbound;

public record HealthError(HealthErrorSeverity severity, String text, String log, String title) {}
