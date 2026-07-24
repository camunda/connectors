/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

/**
 * Configuration (credential) template for reusable REST authentication. Reuses the existing sealed
 * {@link Authentication} union (none / basic / bearer / API key / OAuth / OAuth refresh token),
 * demonstrating that a rich multi-variant auth model maps onto the configuration-template format.
 *
 * <p>Lives in http-base (rather than a single connector's module) so every connector built on the
 * shared {@link Authentication} union — REST, GraphQL, HTTP polling — can bind the same
 * configuration template.
 */
@Configuration(
    id = "io.camunda.connectors:rest-authentication:1",
    version = 1,
    name = "REST Authentication")
public record RestAuthenticationConfiguration(
    @Valid @TemplateProperty(group = "authentication") Authentication authentication) {}
