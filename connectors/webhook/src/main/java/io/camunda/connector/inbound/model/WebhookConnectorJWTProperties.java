package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.Secret;

import java.util.List;

public record WebhookConnectorJWTProperties (@Secret String jwkUrl, String jwtRolePath, List<String> requiredPermissions) {}
