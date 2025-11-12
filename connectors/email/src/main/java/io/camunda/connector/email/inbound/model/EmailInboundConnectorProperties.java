/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.email.authentication.InboundAuthentication;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record EmailInboundConnectorProperties(
    @TemplateProperty(group = "authentication", id = "type") @Valid @NotNull
        InboundAuthentication authentication,
    @NestedProperties(addNestedPath = false) @Valid EmailListenerConfig data) {}
