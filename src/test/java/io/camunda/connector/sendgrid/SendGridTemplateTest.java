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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SendGridTemplateTest extends BaseTest {

  private SendGridTemplate sendGridTemplate;
  private Validator validator;

  @BeforeEach
  public void beforeEach() {
    sendGridTemplate = new SendGridTemplate();
    validator = new Validator();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistContentField() {
    sendGridTemplate.setId(ACTUAL_ID);
    sendGridTemplate.validateWith(validator);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Evaluation failed with following errors: Property required: Dynamic Email Template - Template Data");
  }

  @Test
  void validate_shouldPassWhenAllFieldsExist() {
    sendGridTemplate.setId(ACTUAL_ID);
    sendGridTemplate.validateWith(validator);
    Map<String, String> data = new HashMap<>();
    data.put(SAMPLE, ACTUAL_DATA_VALUE);
    sendGridTemplate.setData(data);

    assertThat(sendGridTemplate.getId()).isEqualTo(ACTUAL_ID);
    assertThat(sendGridTemplate.getData().get(SAMPLE)).isEqualTo(ACTUAL_DATA_VALUE);
  }
}
