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
package io.camunda.connector.optimizer;

import io.camunda.connector.generator.dsl.*;
import java.util.List;
import java.util.Set;

/** Helper class for creating test templates with the IR. */
public final class TestTemplates {

  private TestTemplates() {}

  /**
   * Creates a minimal ElementTemplate with the given properties.
   *
   * @param properties the properties to include
   * @return a minimal template
   */
  public static ElementTemplate template(Property... properties) {
    return new ElementTemplate(
        "test-template",
        "Test Template",
        1,
        null,
        null,
        null,
        null,
        Set.of("bpmn:ServiceTask"), // appliesTo
        new ElementTemplate.ElementTypeWrapper("bpmn:ServiceTask", null, null), // elementType
        List.of(), // groups
        List.of(properties),
        null);
  }

  /** Creates a hidden property with minimal configuration. */
  public static HiddenProperty hiddenProperty(
      String id, String value, PropertyBinding binding, PropertyCondition condition) {
    return (HiddenProperty)
        HiddenProperty.builder().id(id).value(value).binding(binding).condition(condition).build();
  }

  /** Creates a hidden property without a condition. */
  public static HiddenProperty hiddenProperty(String id, String value, PropertyBinding binding) {
    return hiddenProperty(id, value, binding, null);
  }

  /** Creates a ZeebeInput binding. */
  public static PropertyBinding.ZeebeInput zeebeInput(String name) {
    return new PropertyBinding.ZeebeInput(name);
  }

  /** Creates a ZeebeTaskHeader binding. */
  public static PropertyBinding.ZeebeTaskHeader zeebeTaskHeader(String key) {
    return new PropertyBinding.ZeebeTaskHeader(key);
  }

  /** Creates an Equals condition. */
  public static PropertyCondition.Equals equalsCondition(String property, String value) {
    return new PropertyCondition.Equals(property, value);
  }

  /** Creates a OneOf condition. */
  public static PropertyCondition.OneOf oneOf(String property, String... values) {
    return new PropertyCondition.OneOf(property, List.of(values));
  }

  /** Creates a OneOf condition from a list. */
  public static PropertyCondition.OneOf oneOf(String property, List<String> values) {
    return new PropertyCondition.OneOf(property, values);
  }

  /** Creates a dropdown property with choices and no binding (legacy helper). */
  public static DropdownProperty dropdownProperty(
      String id, String value, DropdownProperty.DropdownChoice... choices) {
    return dropdownProperty(id, value, null, choices);
  }

  /** Creates a dropdown property with choices and a specific binding. */
  public static DropdownProperty dropdownProperty(
      String id,
      String value,
      PropertyBinding binding,
      DropdownProperty.DropdownChoice... choices) {
    return new DropdownProperty(
        id,
        null,
        null,
        null,
        value,
        null,
        null,
        null,
        null,
        binding,
        null,
        null,
        List.of(choices),
        null);
  }

  /** Creates a dropdown choice. */
  public static DropdownProperty.DropdownChoice choice(String value) {
    return choice(value, value);
  }

  /** Creates a dropdown choice with name and value. */
  public static DropdownProperty.DropdownChoice choice(String name, String value) {
    return new DropdownProperty.DropdownChoice(name, value);
  }

  /** Creates a string property. */
  public static StringProperty stringProperty(String id, String group) {
    return (StringProperty) StringProperty.builder().id(id).group(group).build();
  }

  /** Creates a string property without a group. */
  public static StringProperty stringProperty(String id) {
    return stringProperty(id, null);
  }

  /** Creates a hidden property with a group. */
  public static HiddenProperty hiddenPropertyWithGroup(String id, String group) {
    return (HiddenProperty) HiddenProperty.builder().id(id).group(group).build();
  }
}
