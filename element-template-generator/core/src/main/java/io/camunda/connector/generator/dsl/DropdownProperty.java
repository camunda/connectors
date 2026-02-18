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
import java.util.List;

public final class DropdownProperty extends Property {

  public static final String TYPE = "Dropdown";

  private final List<DropdownChoice> choices;

  public DropdownProperty(
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
      List<DropdownChoice> choices,
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
    this.choices = choices;
  }

  public List<DropdownChoice> getChoices() {
    return choices;
  }

  public record DropdownChoice(String name, String value) {}

  public static DropdownPropertyBuilder builder() {
    return new DropdownPropertyBuilder();
  }

  public static class DropdownPropertyBuilder extends PropertyBuilder {

    private List<DropdownChoice> choices;

    public DropdownPropertyBuilder() {}

    public DropdownPropertyBuilder choices(List<DropdownChoice> choices) {
      this.choices = choices;
      return this;
    }

    public DropdownProperty build() {
      if (value != null && !(value instanceof String)) {
        throw new IllegalStateException("Value of a dropdown property must be a string");
      }
      return new DropdownProperty(
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
          choices,
          exampleValue);
    }
  }
}
