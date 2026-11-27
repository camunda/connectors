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
package io.camunda.connector.e2e.probes;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;

/** Answers from the password, so each test picks the validator's behavior through the input. */
public class TestCredentialValidator
    implements ConfigurationValidator<TestCredentialValidator.TestCredential> {

  public static final String CONFIGURATION_ID = "io.camunda:e2e-test-credential:1";

  @Configuration(id = CONFIGURATION_ID, name = "E2E test credential")
  public record TestCredential(String password) {}

  @Override
  public ConfigurationValidationResult validate(TestCredential configuration) {
    return switch (configuration.password()) {
      case "valid" -> ConfigurationValidationResult.success();
      case "wrong" ->
          ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, "Wrong password.");
      default -> throw new IllegalStateException("The validator failed.");
    };
  }
}
