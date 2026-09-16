/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.junit.jupiter.api.Test;

class MicrosoftEntraConfigurationTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void declaresTheAgreedIdAndName() {
    Configuration annotation = MicrosoftEntraConfiguration.class.getAnnotation(Configuration.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.id()).isEqualTo("io.camunda.connectors:microsoft-entra:1");
    assertThat(annotation.version()).isEqualTo(1);
    assertThat(annotation.name()).isEqualTo("Microsoft Entra ID");
  }

  @Test
  void deserializesClientCredentials() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {"authentication":{"type":"clientCredentials","clientId":"client-id",\
            "tenantId":"tenant-id","clientSecret":"client-secret"}}
            """,
            MicrosoftEntraConfiguration.class);

    assertThat(configuration.authentication())
        .isInstanceOf(ClientCredentialsAuthentication.class)
        .extracting(a -> ((ClientCredentialsAuthentication) a).tenantId())
        .isEqualTo("tenant-id");
  }

  @Test
  void deserializesBearerToken() throws Exception {
    var configuration =
        objectMapper.readValue(
            "{\"authentication\":{\"type\":\"token\",\"token\":\"bearer-token\"}}",
            MicrosoftEntraConfiguration.class);

    assertThat(configuration.authentication()).isInstanceOf(BearerAuthentication.class);
  }

  @Test
  void deserializesRefreshToken() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {"authentication":{"type":"refresh","token":"refresh-token","clientId":"client-id",\
            "tenantId":"tenant-id","clientSecret":"client-secret"}}
            """,
            MicrosoftEntraConfiguration.class);

    assertThat(configuration.authentication()).isInstanceOf(RefreshTokenAuthentication.class);
  }

  @Test
  void toStringRedactsTheSecret() {
    var configuration =
        new MicrosoftEntraConfiguration(
            new ClientCredentialsAuthentication("client-id", "tenant-id", "the-secret"));

    assertThat(configuration.toString()).doesNotContain("the-secret").contains("[REDACTED]");
  }
}
