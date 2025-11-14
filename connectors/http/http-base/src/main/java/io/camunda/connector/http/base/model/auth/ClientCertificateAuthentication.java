/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@TemplateSubType(id = ClientCertificateAuthentication.TYPE, label = "Client Certificate (mTLS)")
public record ClientCertificateAuthentication(
    @FEEL
        @NotEmpty
        @TemplateProperty(
            group = "authentication",
            label = "Keystore path",
            description =
                "Path to the keystore file (PKCS12 or JKS) containing the client certificate and private key")
        String keystorePath,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Keystore password",
            description = "Password to access the keystore",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String keystorePassword,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Key password",
            description = "Password for the private key (if different from keystore password)",
            optional = true)
        String keyPassword,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Truststore path",
            description =
                "Path to the truststore file (PKCS12 or JKS) containing trusted CA certificates",
            optional = true)
        String truststorePath,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Truststore password",
            description = "Password to access the truststore",
            optional = true)
        String truststorePassword)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "clientCertificate";
}
