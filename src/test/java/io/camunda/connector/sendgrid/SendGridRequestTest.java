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
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.Validator;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SendGridRequestTest extends BaseTest {

  private SendGridRequest request;
  private Validator validator;
  private ConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = new SendGridRequest();
    validator = new Validator();
    context = ConnectorContextBuilder.create().build();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    SendGridEmail from = new SendGridEmail();
    from.setEmail(FROM_EMAIL_VALUE);
    from.setName(SENDER);

    request.setApiKey(API_KEY);
    request.setFrom(from);
    request.setTo(null);
    request.validateWith(validator);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Evaluation failed with following errors: Property required: Receiver, Property required: Email Content");
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    SendGridEmail from = new SendGridEmail();
    from.setEmail(FROM_EMAIL_VALUE);
    from.setName(SENDER);

    SendGridEmail to = new SendGridEmail();
    from.setEmail(TO_EMAIL_VALUE);
    from.setName(RECEIVER);

    request.setApiKey(API_KEY);
    request.setFrom(from);
    request.setTo(to);
    context.replaceSecrets(request);

    assertThat(request.getApiKey()).isEqualTo(API_KEY);
    assertThat(request.getFrom().getEmail()).isEqualTo(from.getEmail());
    assertThat(request.getTo().getEmail()).isEqualTo(to.getEmail());
  }
}
