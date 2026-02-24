/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.aws.agentcore.memory.model.request.AwsAgentCoreMemoryRequest;
import io.camunda.connector.aws.model.impl.AwsAuthentication;
import org.junit.jupiter.api.Test;

class AwsAgentCoreMemorySecretsTest extends BaseTest {

  @Test
  void shouldResolveSecretsForAccessKeyAndSecretKey() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "{{secrets.ACCESS_KEY}}",
            "secretKey": "{{secrets.SECRET_KEY}}"
          },
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

    // when
    var request = context.bindVariables(AwsAgentCoreMemoryRequest.class);

    // then
    var auth = (AwsAuthentication.AwsStaticCredentialsAuthentication) request.getAuthentication();
    assertThat(auth.accessKey()).isEqualTo(ActualValue.ACCESS_KEY);
    assertThat(auth.secretKey()).isEqualTo(ActualValue.SECRET_KEY);
  }

  @Test
  void shouldRedactSecretsInToString() {
    // given
    var input =
        """
        {
          "authentication": {
            "type": "credentials",
            "accessKey": "{{secrets.ACCESS_KEY}}",
            "secretKey": "{{secrets.SECRET_KEY}}"
          },
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
    var request = context.bindVariables(AwsAgentCoreMemoryRequest.class);

    // when
    String toStringResult = request.getAuthentication().toString();

    // then
    assertThat(toStringResult).doesNotContain(ActualValue.ACCESS_KEY);
    assertThat(toStringResult).doesNotContain(ActualValue.SECRET_KEY);
    assertThat(toStringResult).contains("[REDACTED]");
  }
}
