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
package io.camunda.connector.generator.dsl;

import io.camunda.connector.generator.java.annotation.FeelMode;

public final class NumberProperty extends Property {

  public static final String TYPE = "Number";

  public NumberProperty(
      String name,
      String label,
      String description,
      Boolean required,
      Number value,
      GeneratedValue generatedValue,
      PropertyConstraints constraints,
      FeelMode feel,
      String group,
      PropertyBinding binding,
      PropertyCondition condition,
      String tooltip,
      Object exampleValue) {
    super(
        name,
        label,
        description,
        required,
        value,
        generatedValue,
        constraints,
        feel,
        group,
        binding,
        condition,
        tooltip,
        exampleValue,
        TYPE);
  }

  public static NumberPropertyBuilder builder() {
    return new NumberPropertyBuilder();
  }

  public static class NumberPropertyBuilder extends PropertyBuilder {

    private NumberPropertyBuilder() {}

    @Override
    public NumberProperty build() {
      if (value != null && !(value instanceof Number)) {
        throw new IllegalStateException("Value of a Number property must be a Number");
      }
      return new NumberProperty(
          id,
          label,
          description,
          optional,
          (Number) value,
          generatedValue,
          constraints,
          feel,
          group,
          binding,
          condition,
          tooltip,
          exampleValue);
    }
  }
}
