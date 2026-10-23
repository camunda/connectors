/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.box.model.BoxRequest.CredentialAuthentication;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Reusable Box credential. It wraps the whole authentication choice rather than one variant, so a
 * single chooser covers every long-lived Box login (see {@link CredentialAuthentication}).
 */
@Configuration(id = "io.camunda.connectors:box-credential:1", version = 1, name = "Box Credential")
public record BoxCredentialConfiguration(
    @Valid @NotNull @TemplateProperty(group = "authentication", id = "authentication")
        CredentialAuthentication authentication) {}
