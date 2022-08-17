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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.Validator;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqsConnectorRequestTest extends BaseTest {

  private SqsConnectorRequest request;
  private Validator validator;
  private ConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = GSON.fromJson(DEFAULT_REQUEST_BODY, SqsConnectorRequest.class);
    validator = new Validator();

    context =
        ConnectorContextBuilder.create()
            .secret(SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(QUEUE_REGION_KEY, ACTUAL_QUEUE_REGION)
            .secret(QUEUE_URL_KEY, ACTUAL_QUEUE_URL)
            .build();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    // Given request , where one field is null
    request.getQueue().setMessageBody(null);
    // When request validate
    request.validateWith(validator);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    // Then we except exception with message
    assertTrue(thrown.getMessage().contains("Property required:"));
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceMessageBody() {
    // Given request with message body
    request.getQueue().setMessageBody(SECRETS + SQS_MESSAGE_BODY);
    ConnectorContext context =
        ConnectorContextBuilder.create().secret(SQS_MESSAGE_BODY, WRONG_MESSAGE_BODY).build();
    // When replace secrets
    context.replaceSecrets(request);
    // Then expect that message body will be same as was
    assertEquals(request.getQueue().getMessageBody(), SECRETS + SQS_MESSAGE_BODY);
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    // Given request with secrets. all secrets look like 'secrets.KEY'
    request.getAuthentication().setAccessKey(SECRETS + ACCESS_KEY);
    request.getAuthentication().setSecretKey(SECRETS + SECRET_KEY);
    request.getQueue().setUrl(SECRETS + QUEUE_URL_KEY);

    // When replace secrets
    context.replaceSecrets(request);
    // Then
    assertEquals(request.getAuthentication().getSecretKey(), ACTUAL_SECRET_KEY);
    assertEquals(request.getAuthentication().getAccessKey(), ACTUAL_ACCESS_KEY);
    assertEquals(request.getQueue().getUrl(), ACTUAL_QUEUE_URL);
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceSecretsIfTheyDidNotStartFromSecretsWord() {
    // Given request with data that not started from secrets. and context with secret store
    request.getAuthentication().setSecretKey(SECRET_KEY);
    request.getAuthentication().setAccessKey(ACCESS_KEY);
    request.getQueue().setUrl(QUEUE_URL_KEY);
    // When replace secrets
    context.replaceSecrets(request);
    // Then secrets must be not replaced
    assertEquals(request.getAuthentication().getSecretKey(), SECRET_KEY);
    assertEquals(request.getAuthentication().getAccessKey(), ACCESS_KEY);
    assertEquals(request.getQueue().getUrl(), QUEUE_URL_KEY);
  }
}
