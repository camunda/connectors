/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;

/**
 * Mutual TLS (client certificate) configuration. All values are PEM-encoded and supplied as dynamic
 * properties, so they can come from process variables or {@code {{secrets.X}}}.
 */
public record ClientTls(
    @FEEL
        @TemplateProperty(
            group = "tls",
            label = "Client certificate (PEM)",
            optional = true,
            type = PropertyType.Text,
            tooltip =
                "PEM-encoded client certificate chain presented to the server for mTLS. "
                    + "Provide together with the private key.")
        String clientCertificate,
    @FEEL
        @TemplateProperty(
            group = "tls",
            label = "Client private key (PEM)",
            optional = true,
            type = PropertyType.Text,
            tooltip = "PEM-encoded private key (PKCS#1, PKCS#8 or EC), optionally encrypted.")
        String clientPrivateKey,
    @FEEL
        @TemplateProperty(
            group = "tls",
            label = "Private key password",
            optional = true,
            tooltip = "Password protecting the private key. Leave empty if it is not encrypted.")
        String privateKeyPassword,
    @FEEL
        @TemplateProperty(
            group = "tls",
            label = "Trusted CA certificate (PEM)",
            optional = true,
            type = PropertyType.Text,
            tooltip =
                "Optional PEM-encoded CA certificate(s) used to validate the server. "
                    + "If empty, the JVM's default trust store is used.")
        String trustedCertificate) {}
