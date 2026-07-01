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

public abstract class PropertyBuilder {

  protected String id;
  protected String label;
  protected String description;
  protected Boolean optional;
  protected Object value;
  protected Property.GeneratedValue generatedValue;
  protected PropertyConstraints constraints;
  protected FeelMode feel;
  protected String group;
  protected PropertyBinding binding;
  protected String type;
  protected PropertyCondition condition;
  protected String tooltip;
  protected String placeholder;
  protected Object exampleValue;
  protected String language;

  protected PropertyBuilder() {}

  public String getId() {
    return id;
  }

  public PropertyBinding getBinding() {
    return binding;
  }

  public PropertyCondition getCondition() {
    return condition;
  }

  public String getGroup() {
    return group;
  }

  public PropertyBuilder id(String name) {
    this.id = name;
    return this;
  }

  public PropertyBuilder label(String label) {
    this.label = label;
    return this;
  }

  /**
   * Sets the property help text.
   *
   * <p>As of <a href="https://github.com/camunda/connectors/issues/7470">#7470</a> the generator
   * emits property help text as a {@code tooltip} (shown on hover) instead of an always-visible
   * {@code description}. Any value passed here is therefore mapped to {@link #tooltip(String)},
   * unless an explicit tooltip has already been set (an explicit tooltip always wins). To keep an
   * always-visible description for a specific property, edit the generated template manually.
   */
  public PropertyBuilder description(String description) {
    if (description != null
        && !description.isBlank()
        && (this.tooltip == null || this.tooltip.isBlank())) {
      this.tooltip = description;
    }
    return this;
  }

  public PropertyBuilder optional(boolean optional) {
    this.optional = optional;
    return this;
  }

  public PropertyBuilder value(Object value) {
    if (generatedValue != null) {
      throw new IllegalStateException("Generated value is already set");
    }
    this.value = value;
    return this;
  }

  public PropertyBuilder generatedValue() {
    if (value != null) {
      throw new IllegalStateException("Value is already set");
    }
    this.generatedValue = new Property.GeneratedValue("uuid");
    return this;
  }

  public PropertyBuilder constraints(PropertyConstraints constraints) {
    this.constraints = constraints;
    return this;
  }

  public PropertyBuilder feel(FeelMode feel) {
    this.feel = feel;
    return this;
  }

  public PropertyBuilder binding(PropertyBinding binding) {
    this.binding = binding;
    return this;
  }

  public PropertyBuilder condition(PropertyCondition condition) {
    this.condition = condition;
    return this;
  }

  public PropertyBuilder group(String group) {
    this.group = group;
    return this;
  }

  public PropertyBuilder tooltip(String tooltip) {
    this.tooltip = tooltip;
    return this;
  }

  public PropertyBuilder placeholder(String placeholder) {
    this.placeholder = placeholder;
    return this;
  }

  public PropertyBuilder exampleValue(Object exampleValue) {
    this.exampleValue = exampleValue;
    return this;
  }

  public PropertyBuilder language(String language) {
    this.language = language;
    return this;
  }

  public abstract Property build();
}
