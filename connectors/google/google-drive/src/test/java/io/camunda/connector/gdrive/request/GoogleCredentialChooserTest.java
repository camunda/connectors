/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.gdrive.BaseTest;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.camunda.google.model.AuthenticationType;
import org.junit.jupiter.api.Test;

class GoogleCredentialChooserTest extends BaseTest {

  private static final String RESOURCE = "\"resource\":{\"type\":\"folder\",\"name\":\"f\"}";

  private GoogleDriveRequest bind(String json) {
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(json)
            .build();
    return context.bindVariables(GoogleDriveRequest.class);
  }

  @Test
  void credentialBound_noInline_usesCredentialAuthentication() {
    String json =
        "{\"googleCredential\":{\"authentication\":{\"authType\":\"bearer\",\"bearerToken\":\"from-credential\"}},"
            + RESOURCE
            + "}";

    GoogleDriveRequest request = bind(json);

    assertThat(request.getAuthentication()).isNotNull();
    assertThat(request.getAuthentication().authType()).isEqualTo(AuthenticationType.BEARER);
    assertThat(request.getAuthentication().bearerToken()).isEqualTo("from-credential");
  }

  @Test
  void noCredential_inlinePresent_usesInlineAuthentication() {
    String json =
        "{\"authentication\":{\"authType\":\"bearer\",\"bearerToken\":\"from-inline\"},"
            + RESOURCE
            + "}";

    GoogleDriveRequest request = bind(json);

    assertThat(request.getAuthentication().bearerToken()).isEqualTo("from-inline");
  }

  @Test
  void bothPresent_credentialWins() {
    String json =
        "{\"googleCredential\":{\"authentication\":{\"authType\":\"bearer\",\"bearerToken\":\"from-credential\"}},"
            + "\"authentication\":{\"authType\":\"bearer\",\"bearerToken\":\"from-inline\"},"
            + RESOURCE
            + "}";

    GoogleDriveRequest request = bind(json);

    assertThat(request.getAuthentication().bearerToken()).isEqualTo("from-credential");
  }

  @Test
  void neitherPresent_bindingFailsNamingBothSources() {
    String json = "{" + RESOURCE + "}";

    assertThatThrownBy(() -> bind(json))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(
            "No authentication provided by the credential or the element template");
  }

  @Test
  void credentialBound_leftoverInlineDiscriminatorIgnored() {
    String json =
        "{\"googleCredential\":{\"authentication\":{\"authType\":\"bearer\",\"bearerToken\":\"from-credential\"}},"
            + "\"authentication\":{\"authType\":\"refresh\"},"
            + RESOURCE
            + "}";

    GoogleDriveRequest request = bind(json);

    assertThat(request.getAuthentication().bearerToken()).isEqualTo("from-credential");
  }
}
