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

import io.camunda.connector.api.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SendGridContentTest extends BaseTest {

  private SendGridContent content;
  private Validator validator;

  @BeforeEach
  public void beforeEach() {
    content = new SendGridContent();
    validator = new Validator();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistContentField() {
    content.setSubject("Test");
    content.setType("Content Type");
    content.validateWith(validator);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Evaluation failed with following errors: Property required: Email Content - Body");
  }

  @Test
  void validate_shouldPassWhenAllFieldsExist() {
    content.setSubject(ACTUAL_SUBJECT);
    content.setType(ACTUAL_TYPE);
    content.setValue(ACTUAL_VALUE);
    content.validateWith(validator);

    assertThat(content.getSubject()).isEqualTo(ACTUAL_SUBJECT);
    assertThat(content.getType()).isEqualTo(ACTUAL_TYPE);
    assertThat(content.getValue()).isEqualTo(ACTUAL_VALUE);
  }
}
