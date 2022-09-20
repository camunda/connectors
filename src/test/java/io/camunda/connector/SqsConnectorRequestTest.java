/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqsConnectorRequestTest extends BaseTest {

  private SqsConnectorRequest request;
  private OutboundConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = GSON.fromJson(DEFAULT_REQUEST_BODY, SqsConnectorRequest.class);

    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(SQS_QUEUE_REGION, ACTUAL_QUEUE_REGION)
            .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
            .build();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    // Given request , where one field is null
    request.getQueue().setMessageBody(null);
    // When request validate
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request),
            "ConnectorInputException was expected");
    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceMessageBody() {
    // Given request with message body
    request.getQueue().setMessageBody(SECRETS + SQS_MESSAGE_BODY);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .secret(SQS_MESSAGE_BODY, WRONG_MESSAGE_BODY)
            .build();
    // When replace secrets
    context.replaceSecrets(request);
    // Then expect that message body will be same as was
    assertThat(request.getQueue().getMessageBody()).isEqualTo(SECRETS + SQS_MESSAGE_BODY);
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    // Given request with secrets. all secrets look like 'secrets.KEY'
    request.getAuthentication().setAccessKey(SECRETS + AWS_ACCESS_KEY);
    request.getAuthentication().setSecretKey(SECRETS + AWS_SECRET_KEY);
    request.getQueue().setUrl(SECRETS + SQS_QUEUE_URL);

    // When replace secrets
    context.replaceSecrets(request);
    // Then
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getQueue().getUrl()).isEqualTo(ACTUAL_QUEUE_URL);
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceSecretsIfTheyDidNotStartFromSecretsWord() {
    // Given request with data that not started from secrets. and context with secret store
    request.getAuthentication().setSecretKey(AWS_SECRET_KEY);
    request.getAuthentication().setAccessKey(AWS_ACCESS_KEY);
    request.getQueue().setUrl(SQS_QUEUE_URL);
    // When replace secrets
    context.replaceSecrets(request);
    // Then secrets must be not replaced
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(AWS_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(AWS_ACCESS_KEY);
    assertThat(request.getQueue().getUrl()).isEqualTo(SQS_QUEUE_URL);
  }
}
