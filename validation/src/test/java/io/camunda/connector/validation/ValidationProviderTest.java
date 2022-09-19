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
package io.camunda.connector.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchException;

import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class ValidationProviderTest {
  private static final ValidationProvider VALIDATION_PROVIDER = new DefaultValidationProvider();

  @Test
  void shouldPassValidationWithValidObject() {
    // given
    User user =
        mockUser(
            "Peter Parker", "peter.parker@marvel.com", "Definitely not a super hero", 18, true);
    // then
    assertThatNoException()
        .isThrownBy(
            // when
            () -> VALIDATION_PROVIDER.validate(user));
  }

  @Test
  void shouldFailValidationWithInvalidObject() {
    // given
    User user = mockUser("Green Goblin", "weird", "Definitely not a trustworthy person", 60, false);
    // when
    Exception exception = catchException(() -> VALIDATION_PROVIDER.validate(user));
    // then
    assertThat(exception)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageStartingWith(
            "jakarta.validation.ValidationException: Found constraints violated while validating input:")
        .hasMessageContaining("- working:")
        .hasMessageContaining("- email: Email should be valid");
  }

  private User mockUser(String name, String email, String aboutMe, int age, boolean working) {
    User user = new User();
    user.setAge(age);
    user.setEmail(email);
    user.setName(name);
    user.setAboutMe(aboutMe);
    user.setWorking(working);
    return user;
  }
}
