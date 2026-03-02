/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.common.auth.ClientCredentialsAuthentication;
import io.camunda.connector.microsoft.common.auth.MicrosoftAuthentication;
import io.camunda.connector.microsoft.common.auth.RefreshTokenAuthentication;
import io.camunda.connector.microsoft.email.model.config.EmailPollingConfig;
import io.camunda.connector.microsoft.email.model.config.EmailProcessingOperation;
import io.camunda.connector.microsoft.email.model.config.FilterCriteria;
import io.camunda.connector.microsoft.email.model.config.Folder;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MsInboundEmailPropertiesValidationTest {

  private InboundConnectorContext context;

  private static MicrosoftAuthentication validAuthentication() {
    return new ClientCredentialsAuthentication("client-id", "tenant-id", "client-secret");
  }

  private static MicrosoftAuthentication validRefreshTokenAuthentication() {
    return new RefreshTokenAuthentication(
        "refresh-token", "client-id", "tenant-id", "client-secret");
  }

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

  @Nested
  class AuthenticationValidation {

    @Test
    void validate_shouldFail_whenAuthenticationIsNull() {
      // Given
      var properties = new MsInboundEmailProperties(null, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenTenantIdIsBlank() {
      // Given
      var auth = new ClientCredentialsAuthentication("client-id", "", "client-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenClientIdIsBlank() {
      // Given
      var auth = new ClientCredentialsAuthentication("", "tenant-id", "client-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenClientSecretIsBlank() {
      // Given
      var auth = new ClientCredentialsAuthentication("client-id", "tenant-id", "");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenRefreshTokenIsBlank() {
      // Given
      var auth = new RefreshTokenAuthentication("", "client-id", "tenant-id", "client-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenRefreshTokenTenantIdIsBlank() {
      // Given
      var auth = new RefreshTokenAuthentication("refresh-token", "client-id", "", "client-secret");
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldSucceed_withRefreshTokenAndNoClientSecret() {
      // Given - refresh token auth allows optional client secret
      var auth = new RefreshTokenAuthentication("refresh-token", "client-id", "tenant-id", null);
      var properties = new MsInboundEmailProperties(auth, validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties).isNotNull();
      assertThat(boundProperties.authentication()).isInstanceOf(RefreshTokenAuthentication.class);
    }
  }

  @Nested
  class PollingConfigValidation {

    @Test
    void validate_shouldFail_whenUserIdIsBlank() {
      // Given
      var pollingConfig =
          new EmailPollingConfig(
              "",
              new Folder.FolderByName("inbox"),
              Duration.ofSeconds(30),
              new FilterCriteria.SimpleConfiguration(true, null, null));
      var properties =
          new MsInboundEmailProperties(validAuthentication(), pollingConfig, validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenFolderNameIsBlank() {
      // Given
      var pollingConfig =
          new EmailPollingConfig(
              "user@example.com",
              new Folder.FolderByName(""),
              Duration.ofSeconds(30),
              new FilterCriteria.SimpleConfiguration(true, null, null));
      var properties =
          new MsInboundEmailProperties(validAuthentication(), pollingConfig, validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenAdvancedFilterStringIsBlank() {
      // Given
      var pollingConfig =
          new EmailPollingConfig(
              "user@example.com",
              new Folder.FolderByName("inbox"),
              Duration.ofSeconds(30),
              new FilterCriteria.AdvancedConfiguration(""));
      var properties =
          new MsInboundEmailProperties(validAuthentication(), pollingConfig, validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldSucceed_withAdvancedFilterString() {
      // Given
      var pollingConfig =
          new EmailPollingConfig(
              "user@example.com",
              new Folder.FolderByName("inbox"),
              Duration.ofSeconds(30),
              new FilterCriteria.AdvancedConfiguration("isRead eq false"));
      var properties =
          new MsInboundEmailProperties(validAuthentication(), pollingConfig, validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.pollingConfig().filterCriteria())
          .isInstanceOf(FilterCriteria.AdvancedConfiguration.class);
    }
  }

  @Nested
  class OperationValidation {

    @Test
    void validate_shouldFail_whenOperationIsNull() {
      // Given
      var properties =
          new MsInboundEmailProperties(validAuthentication(), validPollingConfig(), null);

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenMoveOperationTargetFolderIsNull() {
      // Given
      var properties =
          new MsInboundEmailProperties(
              validAuthentication(),
              validPollingConfig(),
              new EmailProcessingOperation.MoveOperation(null));

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }

    @Test
    void validate_shouldFail_whenMoveOperationTargetFolderNameIsBlank() {
      // Given
      var properties =
          new MsInboundEmailProperties(
              validAuthentication(),
              validPollingConfig(),
              new EmailProcessingOperation.MoveOperation(new Folder.FolderByName("")));

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When/Then
      ConnectorInputException thrown =
          assertThrows(
              ConnectorInputException.class,
              () -> context.bindProperties(MsInboundEmailProperties.class));
      assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
    }
  }

  @Nested
  class ValidPropertiesValidation {

    @Test
    void validate_shouldSucceed_withValidClientCredentialsProperties() {
      // Given
      var properties =
          new MsInboundEmailProperties(
              validAuthentication(), validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties).isNotNull();
      assertThat(boundProperties.authentication())
          .isInstanceOf(ClientCredentialsAuthentication.class);
      var auth = (ClientCredentialsAuthentication) boundProperties.authentication();
      assertThat(auth.tenantId()).isEqualTo("tenant-id");
      assertThat(auth.clientId()).isEqualTo("client-id");
      assertThat(auth.clientSecret()).isEqualTo("client-secret");
    }

    @Test
    void validate_shouldSucceed_withValidRefreshTokenProperties() {
      // Given
      var properties =
          new MsInboundEmailProperties(
              validRefreshTokenAuthentication(), validPollingConfig(), validOperation());

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties).isNotNull();
      assertThat(boundProperties.authentication()).isInstanceOf(RefreshTokenAuthentication.class);
      var auth = (RefreshTokenAuthentication) boundProperties.authentication();
      assertThat(auth.tenantId()).isEqualTo("tenant-id");
      assertThat(auth.clientId()).isEqualTo("client-id");
      assertThat(auth.clientSecret()).isEqualTo("client-secret");
      assertThat(auth.token()).isEqualTo("refresh-token");
    }

    @Test
    void validate_shouldSucceed_withDeleteOperation() {
      // Given
      var properties =
          new MsInboundEmailProperties(
              validAuthentication(),
              validPollingConfig(),
              new EmailProcessingOperation.DeleteOperation(true));

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.operation())
          .isInstanceOf(EmailProcessingOperation.DeleteOperation.class);
    }

    @Test
    void validate_shouldSucceed_withMoveOperation() {
      // Given
      var properties =
          new MsInboundEmailProperties(
              validAuthentication(),
              validPollingConfig(),
              new EmailProcessingOperation.MoveOperation(new Folder.FolderByName("archive")));

      context =
          InboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .properties(properties)
              .build();

      // When
      var boundProperties = context.bindProperties(MsInboundEmailProperties.class);

      // Then
      assertThat(boundProperties.operation())
          .isInstanceOf(EmailProcessingOperation.MoveOperation.class);
    }
  }
}
