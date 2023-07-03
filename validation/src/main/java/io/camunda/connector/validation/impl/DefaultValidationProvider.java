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

import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.ConnectorInputException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

public class DefaultValidationProvider implements ValidationProvider {
  protected static final String LF = "\n";
  protected final ValidatorFactory validatorFactory;

  public DefaultValidationProvider() {
    final var configuration = Validation.byDefaultProvider().configure();
    Optional.ofNullable(getMessageInterpolator()).ifPresent(configuration::messageInterpolator);
    this.validatorFactory = configuration.buildValidatorFactory();
  }

  @Override
  public void validate(Object objectToValidate) {
    Set<ConstraintViolation<Object>> violations =
        validatorFactory.getValidator().validate(objectToValidate);
    if (!violations.isEmpty()) {
      String errorMessage = composeMessage(violations);
      throw new ConnectorInputException(new ValidationException(errorMessage));
    }
  }

  protected MessageInterpolator getMessageInterpolator() {
    try {
      Class.forName("javax.el.ExpressionFactory");
      // return null to use validator factory's default
      return null;
    } catch (Exception e) {
      // Jakarta EL is not present, use message parameter interpolator
      try {
        return new ParameterMessageInterpolator();
      } catch (Exception ex) {
        // Hibernate validator not present, return null to use validator factory's default
        return null;
      }
    }
  }

  protected String composeMessage(Set<ConstraintViolation<Object>> violations) {
    String firstLine = "Found constraints violated while validating input: " + LF;
    return firstLine
        + violations.stream().map(this::buildValidationMessage).collect(Collectors.joining(LF));
  }

  /**
   * We explicitly don't make use of the violation.getMessage() to avoid potential leakage of any
   * property values.
   */
  protected String buildValidationMessage(ConstraintViolation<Object> violation) {
    return " - Property: " + violation.getPropertyPath().toString() + ": Validation failed.";
  }
}
