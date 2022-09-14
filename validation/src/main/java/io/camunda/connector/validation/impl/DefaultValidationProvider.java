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
package io.camunda.connector.validation.impl;

import io.camunda.connector.api.ValidationProvider;
import io.camunda.connector.impl.ConnectorInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultValidationProvider implements ValidationProvider {
  private static final String LF = "\n";
  private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

  @Override
  public void validate(Object objectToValidate) {
    Set<ConstraintViolation<Object>> violations =
        validatorFactory.getValidator().validate(objectToValidate);
    if (!violations.isEmpty()) {
      String errorMessage = composeMessage(violations);
      throw new ConnectorInputException(new ValidationException(errorMessage));
    }
  }

  private String composeMessage(Set<ConstraintViolation<Object>> violations) {
    String firstLine = "Found constraints violated while validating input: " + LF;
    return firstLine
        + violations.stream().map(this::buildValidationMessage).collect(Collectors.joining(LF));
  }

  private String buildValidationMessage(ConstraintViolation<Object> violation) {
    return " - " + violation.getPropertyPath().toString() + ": " + violation.getMessage();
  }
}
