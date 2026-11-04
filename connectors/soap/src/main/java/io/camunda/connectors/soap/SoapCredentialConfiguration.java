/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connectors.soap.SoapConnectorInput.CredentialAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Reusable SOAP credential. It wraps the whole authentication choice rather than one variant, so a
 * single chooser covers every WSS login that can be stored (see {@link CredentialAuthentication}).
 */
@Configuration(
    id = "io.camunda.connectors:soap-credential:1",
    version = 1,
    name = "SOAP Credential")
public record SoapCredentialConfiguration(
    @Valid @NotNull @TemplateProperty(group = "authentication", id = "authentication")
        CredentialAuthentication authentication) {}
