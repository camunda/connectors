/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import org.junit.jupiter.api.Test;

class BedrockKnowledgeBaseInputValidationTest extends BaseTest {

  @Test
  void shouldFailWhenKnowledgeBaseIdMissing() {
    var input =
        """
        {
          "authentication": {"type": "credentials", "accessKey": "{{secrets.AWS_ACCESS_KEY}}", "secretKey": "{{secrets.AWS_SECRET_KEY}}"},
          "configuration": {"region": "us-east-1"},
          "operationDiscriminator": "retrieve",
          "operation": {"operationDiscriminator": "retrieve", "query": "test query", "numberOfResults": 5}
        }
        """;
    var context = getContextBuilderWithSecrets().variables(input).build();
    assertThatThrownBy(() -> context.bindVariables(BedrockKnowledgeBaseRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldFailWhenQueryMissing() {
    var input =
        """
        {
          "authentication": {"type": "credentials", "accessKey": "{{secrets.AWS_ACCESS_KEY}}", "secretKey": "{{secrets.AWS_SECRET_KEY}}"},
          "configuration": {"region": "us-east-1"},
          "knowledgeBaseId": "KB12345TEST",
          "operationDiscriminator": "retrieve",
          "operation": {"operationDiscriminator": "retrieve", "numberOfResults": 5}
        }
        """;
    var context = getContextBuilderWithSecrets().variables(input).build();
    assertThatThrownBy(() -> context.bindVariables(BedrockKnowledgeBaseRequest.class))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void shouldAcceptValidInput() {
    var input =
        """
        {
          "authentication": {"type": "credentials", "accessKey": "{{secrets.AWS_ACCESS_KEY}}", "secretKey": "{{secrets.AWS_SECRET_KEY}}"},
          "configuration": {"region": "us-east-1"},
          "knowledgeBaseId": "KB12345TEST",
          "operationDiscriminator": "retrieve",
          "operation": {"operationDiscriminator": "retrieve", "query": "What does the policy cover?", "numberOfResults": 5}
        }
        """;
    var context = getContextBuilderWithSecrets().variables(input).build();
    var request = context.bindVariables(BedrockKnowledgeBaseRequest.class);
    assertThat(request.getKnowledgeBaseId()).isEqualTo("KB12345TEST");
  }
}
