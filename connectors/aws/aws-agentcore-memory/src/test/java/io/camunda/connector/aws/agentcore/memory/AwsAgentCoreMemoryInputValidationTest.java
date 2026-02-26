/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.aws.agentcore.memory.model.request.AwsAgentCoreMemoryRequest;
import org.junit.jupiter.api.Test;

class AwsAgentCoreMemoryInputValidationTest extends BaseTest {

  @Test
  void shouldFailWhenMemoryIdIsBlank() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "testKey",
            "secretKey": "testSecret"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "",
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/test",
            "searchQuery": "test query"
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then
    assertThatThrownBy(() -> context.bindVariables(AwsAgentCoreMemoryRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldFailWhenNamespaceIsBlank() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "testKey",
            "secretKey": "testSecret"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-123",
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "",
            "searchQuery": "test query"
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then
    assertThatThrownBy(() -> context.bindVariables(AwsAgentCoreMemoryRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldFailWhenSearchQueryIsBlank() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "testKey",
            "secretKey": "testSecret"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-123",
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/test",
            "searchQuery": ""
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then
    assertThatThrownBy(() -> context.bindVariables(AwsAgentCoreMemoryRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldFailWhenAuthenticationIsNull() {
    // given
    var input =
        """
        {
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-123",
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/test",
            "searchQuery": "test query"
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then
    assertThatThrownBy(() -> context.bindVariables(AwsAgentCoreMemoryRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldFailWhenMaxResultsBelowMinimum() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "testKey",
            "secretKey": "testSecret"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-123",
          "maxResults": 0,
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/test",
            "searchQuery": "test query"
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then
    assertThatThrownBy(() -> context.bindVariables(AwsAgentCoreMemoryRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldFailWhenMaxResultsAboveMaximum() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "testKey",
            "secretKey": "testSecret"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-123",
          "maxResults": 101,
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/test",
            "searchQuery": "test query"
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then
    assertThatThrownBy(() -> context.bindVariables(AwsAgentCoreMemoryRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldPassValidationWithValidInput() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "testKey",
            "secretKey": "testSecret"
          },
          "configuration": {
            "region": "us-east-1"
          },
          "memoryId": "mem-123",
          "maxResults": 20,
          "operation": {
            "operationDiscriminator": "retrieve",
            "namespace": "/test",
            "searchQuery": "test query"
          }
        }
        """;

    var context = getContextBuilderWithSecrets().variables(input).build();

    // when/then — should not throw
    var request = context.bindVariables(AwsAgentCoreMemoryRequest.class);
    org.assertj.core.api.Assertions.assertThat(request.getMemoryId()).isEqualTo("mem-123");
  }
}
