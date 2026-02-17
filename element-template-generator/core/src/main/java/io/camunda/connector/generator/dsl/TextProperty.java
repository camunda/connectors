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

public final class TextProperty extends Property {

  public static final String TYPE = "Text";

  public TextProperty(
      String name,
      String label,
      String description,
      Boolean required,
      String value,
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

  public static TextPropertyBuilder builder() {
    return new TextPropertyBuilder();
  }

  public static class TextPropertyBuilder extends PropertyBuilder {

    private TextPropertyBuilder() {}

    @Override
    public TextProperty build() {
      if (feel == null) {
        feel = FeelMode.optional;
      }
      if (value != null && !(value instanceof String)) {
        throw new IllegalStateException("Value of a text property must be a string");
      }
      return new TextProperty(
          id,
          label,
          description,
          optional,
          (String) value,
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
