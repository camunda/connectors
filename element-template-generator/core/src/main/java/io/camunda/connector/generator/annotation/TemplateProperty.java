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
package io.camunda.connector.generator.annotation;

import io.camunda.connector.generator.dsl.Property.FeelMode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used to customize the generated element template for a property.
 *
 * <p>By default, the generator will create a property for each field of the class and use the field
 * name as the property ID. Label will also be derived from the field name. The type of the property
 * will be determined based on the field type.
 *
 * <p>This annotation can be used to customize the generated element template for a property and
 * apply custom labels, descriptions, conditions, or even override the property type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface TemplateProperty {

  /** Custom property ID that can be referenced in conditions */
  String id() default "";

  /** Custom label of the property */
  String label() default "";

  /** Description of the property */
  String description() default "";

  /** Whether the property should be marked as optional in the element template */
  boolean optional() default false;

  /**
   * Overrides the property type. By default, the generator will use the field type to determine the
   * property type. Most primitive fields will be mapped to String properties by default.
   *
   * <p>If you use the {@link PropertyType#Dropdown} type, you can also specify the choices for the
   * dropdown using the {@link #choices()} property.
   */
  PropertyType type() default PropertyType.Unknown;

  /**
   * If the property type is set to {@link PropertyType#Dropdown}, you can use this property to
   * specify the choices for the dropdown. Otherwise, this property will be ignored.
   */
  DropdownPropertyChoice[] choices() default {};

  /** Whether the property should support FEEL expressions */
  FeelMode feel() default FeelMode.optional;

  /** Default value for the property */
  String defaultValue() default "";

  /**
   * Group ID for the property.
   *
   * <p>Use {@link ElementTemplate#propertyGroups()} to override group labels or ordering.
   */
  String group() default "";

  /**
   * Whether to add the nested path to the property name. Consider the example:
   *
   * <pre>{@code
   * MyNestedType foo;
   *
   * }</pre>
   *
   * where MyNestedType is defined like this:
   *
   * <pre>{@code
   * record MyNestedType(String bar) {}
   *
   * }</pre>
   *
   * In the example above, if this property is set to true, the property name in the generated
   * element template will be "foo.bar". If it is set to false, the property name will be "bar".
   *
   * <p>Disabling this setting can be used to define custom element template structure, overriding
   * the default behavior of nesting properties.
   */
  boolean addNestedPath() default true;

  /**
   * Condition for the property. Conditions can reference other properties to decide whether the
   * property should be rendered by the Modeler. This field can either be an 'Equals' condition or a
   * 'One of' condition.
   *
   * <p>It is recommended to explicitly define custom IDs for properties that are used in
   * conditions.
   */
  PropertyCondition condition() default @PropertyCondition(property = "");

  /**
   * Can be used to make the class field invisible to the template generator, e.g. exclude fields
   * that contain implementation details or define constants.
   */
  boolean ignore() default false;

  enum PropertyType {
    Boolean,
    Dropdown,
    Hidden,
    String,
    Text,
    Unknown
  }

  @interface PropertyCondition {
    String property();

    String equals() default "";

    String[] oneOf() default {};
  }

  @interface DropdownPropertyChoice {
    String value();

    String label();
  }
}
