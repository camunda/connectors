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

public final class HiddenProperty extends Property {

  public static final String TYPE = "Hidden";

  public HiddenProperty(
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
      PropertyCondition condition) {
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
        null,
        null,
        TYPE);
  }

  public static HiddenPropertyBuilder builder() {
    return new HiddenPropertyBuilder();
  }

  public static class HiddenPropertyBuilder extends PropertyBuilder {

    private HiddenPropertyBuilder() {}

    @Override
    public HiddenProperty build() {
      if (value != null && !(value instanceof String)) {
        throw new IllegalStateException("Value of a hidden property must be a string");
      }
      return new HiddenProperty(
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
          condition);
    }
  }
}
