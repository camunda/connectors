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

import java.util.ArrayList;
import java.util.List;

/**
 * A generic validator.
 *
 * <p>This class is intended to validate the properties of supplied data objects. Users can
 * implement their own local validation methods if necessary and add custom error messages to an
 * instance of this validator class.
 */
public class Validator {
  protected static final String PROPERTIES_MISSING_MSG =
      "Evaluation failed with following errors: %s";
  protected static final String PROPERTY_REQUIRED_MSG = "Property required: ";

  private final List<String> errorMessages = new ArrayList<>();

  /**
   * Adds a custom error message to the overall list of errors.
   *
   * @param errorMessage - message to be added
   */
  public void addErrorMessage(final String errorMessage) {
    if (errorMessage != null) {
      errorMessages.add(errorMessage);
    }
  }

  /**
   * Indicates that the property is required.
   *
   * <p>Checks for <code>null</code> for all objects. If the object is <code>null</code>, adds a
   * preformatted error message with <code>propertyName</code>.
   *
   * <p>Provides additional check for {@link CharSequence} objects. If the given object contains
   * only whitespaces, adds a preformatted error message with <code>propertyName</code>.
   *
   * @param property - required property
   * @param propertyName - property name; used to add into the error list
   */
  public void require(final Object property, final String propertyName) {
    if (property == null || isBlank(property)) {
      addErrorMessage(PROPERTY_REQUIRED_MSG + propertyName);
    }
  }

  protected boolean isBlank(Object property) {
    return property instanceof CharSequence
        && ((CharSequence) property).chars().allMatch(Character::isWhitespace);
  }

  /**
   * Evaluates whether validator contains errors.
   *
   * @throws IllegalArgumentException if there were errors.
   */
  public void evaluate() {
    if (!errorMessages.isEmpty()) {
      throw new IllegalArgumentException(getEvaluationResultMessage());
    }
  }

  private String getEvaluationResultMessage() {
    return String.format(PROPERTIES_MISSING_MSG, String.join(", ", errorMessages));
  }
}
