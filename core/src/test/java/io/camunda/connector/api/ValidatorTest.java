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
package io.camunda.connector.api;

import static io.camunda.connector.api.Validator.PROPERTIES_MISSING_MSG;
import static io.camunda.connector.api.Validator.PROPERTY_REQUIRED_MSG;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidatorTest {

  private Validator validator;

  @BeforeEach
  void setup() {
    validator = new Validator();
  }

  @Test
  void shouldRaiseException_WhenEvaluatedWithSingleError() {
    // given
    final String propertyErrorMessage = "Property X is invalid!";
    final String expectedErrorMessage = String.format(PROPERTIES_MISSING_MSG, propertyErrorMessage);
    validator.addErrorMessage(propertyErrorMessage);

    // when
    Throwable exception = assertThrows(IllegalArgumentException.class, validator::evaluate);

    // then
    Assertions.assertThat(exception.getMessage()).isEqualTo(expectedErrorMessage);
  }

  @Test
  void shouldRaiseException_WhenEvaluatedWithMultipleErrors() {
    // given
    final String propertyErrorMessage1 = "Property 1 is invalid!";
    final String propertyErrorMessage2 = "Property 2 is invalid!";
    final String expectedErrorMessage =
        String.format(
            PROPERTIES_MISSING_MSG,
            String.join(", ", asList(propertyErrorMessage1, propertyErrorMessage2)));
    validator.addErrorMessage(propertyErrorMessage1);
    validator.addErrorMessage(propertyErrorMessage2);

    // when
    Throwable exception = assertThrows(IllegalArgumentException.class, validator::evaluate);

    // then
    Assertions.assertThat(exception.getMessage()).isEqualTo(expectedErrorMessage);
  }

  @Test
  void shouldNotRaiseException_WhenEvaluated_IfAddedNullAsProperyName() {
    // given
    final String propertyErrorMessage = null;
    validator.addErrorMessage(propertyErrorMessage);

    // when
    validator.evaluate();

    // then
    // -> all good, evaluated without exception
  }

  @Test
  void shouldRaiseException_WhenEvaluated_IfRequiredObjectIsMissing() {
    // given
    final String propertyName = "AwesomeObject";
    final String expectedErrorMessage =
        String.format(PROPERTIES_MISSING_MSG, PROPERTY_REQUIRED_MSG + propertyName);
    validator.require(null, propertyName);

    // when
    Throwable exception = assertThrows(IllegalArgumentException.class, validator::evaluate);

    // then
    Assertions.assertThat(exception.getMessage()).isEqualTo(expectedErrorMessage);
  }
}
