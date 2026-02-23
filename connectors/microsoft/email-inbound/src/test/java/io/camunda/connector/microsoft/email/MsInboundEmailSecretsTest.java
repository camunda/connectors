/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.email.model.config.EmailPollingConfig;
import io.camunda.connector.microsoft.email.model.config.EmailProcessingOperation;
import io.camunda.connector.microsoft.email.model.config.FilterCriteria;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.config.InboundAuthentication;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MsInboundEmailSecretsTest {

  private InboundConnectorContext context;

  private static final String TENANT_ID_SECRET = "secrets.TENANT_ID";
  private static final String CLIENT_ID_SECRET = "secrets.CLIENT_ID";
  private static final String CLIENT_SECRET_SECRET = "secrets.CLIENT_SECRET";

  private static final String ACTUAL_TENANT_ID = "actual-tenant-id-12345";
  private static final String ACTUAL_CLIENT_ID = "actual-client-id-67890";
  private static final String ACTUAL_CLIENT_SECRET = "actual-client-secret-abcde";

  private static EmailPollingConfig validPollingConfig() {
    return new EmailPollingConfig(
        "user@example.com",
        new Folder.FolderByName("inbox"),
        Duration.ofSeconds(30),
        new FilterCriteria.SimpleConfiguration(true, null, null));
  }

  private static EmailProcessingOperation validOperation() {
    return new EmailProcessingOperation.MarkAsReadOperation();
  }

  private static InboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return InboundConnectorContextBuilder.create()
        .secret("TENANT_ID", ACTUAL_TENANT_ID)
        .secret("CLIENT_ID", ACTUAL_CLIENT_ID)
        .secret("CLIENT_SECRET", ACTUAL_CLIENT_SECRET);
  }

  @Nested
  class AuthenticationSecrets {

    @Test
    void replaceSecrets_shouldReplaceTenantIdSecret() {
      // Given
      var auth = new InboundAuthentication(TENANT_ID_SECRET, "plain-client-id", "plain-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context = getContextBuilderWithSecrets().properties(properties).build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.authentication().tenantId()).isEqualTo(ACTUAL_TENANT_ID);
      assertThat(boundProperties.authentication().clientId()).isEqualTo("plain-client-id");
      assertThat(boundProperties.authentication().clientSecret()).isEqualTo("plain-secret");
    }

    @Test
    void replaceSecrets_shouldReplaceClientIdSecret() {
      // Given
      var auth = new InboundAuthentication("plain-tenant-id", CLIENT_ID_SECRET, "plain-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context = getContextBuilderWithSecrets().properties(properties).build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.authentication().tenantId()).isEqualTo("plain-tenant-id");
      assertThat(boundProperties.authentication().clientId()).isEqualTo(ACTUAL_CLIENT_ID);
      assertThat(boundProperties.authentication().clientSecret()).isEqualTo("plain-secret");
    }

    @Test
    void replaceSecrets_shouldReplaceClientSecretSecret() {
      // Given
      var auth =
          new InboundAuthentication("plain-tenant-id", "plain-client-id", CLIENT_SECRET_SECRET);
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context = getContextBuilderWithSecrets().properties(properties).build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.authentication().tenantId()).isEqualTo("plain-tenant-id");
      assertThat(boundProperties.authentication().clientId()).isEqualTo("plain-client-id");
      assertThat(boundProperties.authentication().clientSecret()).isEqualTo(ACTUAL_CLIENT_SECRET);
    }

    @Test
    void replaceSecrets_shouldReplaceAllAuthSecrets() {
      // Given
      var auth =
          new InboundAuthentication(TENANT_ID_SECRET, CLIENT_ID_SECRET, CLIENT_SECRET_SECRET);
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context = getContextBuilderWithSecrets().properties(properties).build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.authentication().tenantId()).isEqualTo(ACTUAL_TENANT_ID);
      assertThat(boundProperties.authentication().clientId()).isEqualTo(ACTUAL_CLIENT_ID);
      assertThat(boundProperties.authentication().clientSecret()).isEqualTo(ACTUAL_CLIENT_SECRET);
    }
  }

  @Nested
  class MixedSecretsAndPlainValues {

    @Test
    void replaceSecrets_shouldHandleMixOfSecretsAndPlainValues() {
      // Given - mix of secret references and plain values
      var auth =
          new InboundAuthentication(TENANT_ID_SECRET, "plain-client-id", CLIENT_SECRET_SECRET);
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context = getContextBuilderWithSecrets().properties(properties).build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.authentication().tenantId()).isEqualTo(ACTUAL_TENANT_ID);
      assertThat(boundProperties.authentication().clientId()).isEqualTo("plain-client-id");
      assertThat(boundProperties.authentication().clientSecret()).isEqualTo(ACTUAL_CLIENT_SECRET);
    }

    @Test
    void replaceSecrets_shouldPreservePlainValuesWhenNoSecrets() {
      // Given - all plain values, no secrets
      var auth = new InboundAuthentication("plain-tenant", "plain-client", "plain-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context = getContextBuilderWithSecrets().properties(properties).build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.authentication().tenantId()).isEqualTo("plain-tenant");
      assertThat(boundProperties.authentication().clientId()).isEqualTo("plain-client");
      assertThat(boundProperties.authentication().clientSecret()).isEqualTo("plain-secret");
    }
  }
}
