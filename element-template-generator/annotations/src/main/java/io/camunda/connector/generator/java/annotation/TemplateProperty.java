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
package io.camunda.connector.generator.java.annotation;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;

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
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface TemplateProperty {
  boolean OPTIONAL_DEFAULT = false;

  /** Custom property ID that can be referenced in conditions */
  String id() default "";

  /** Custom label of the property */
  String label() default "";

  /** Custom binding name of the property */
  PropertyBinding binding() default @PropertyBinding(name = "");

  /** Description of the property */
  String description() default "";

  /** Whether the property should be marked as optional in the element template */
  boolean optional() default OPTIONAL_DEFAULT;

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

  /**
   * Defines the support for FEEL expressions in the property. By default, for inbound connectors,
   * FEEL is disabled; for outbound connectors, FEEL is optional.
   */
  FeelMode feel() default FeelMode.system_default;

  /** Default value for the property */
  String defaultValue() default "";

  /** Resulting JSON type for the default value */
  DefaultValueType defaultValueType() default DefaultValueType.String;

  String exampleValue() default "";

  /**
   * Group ID for the property.
   *
   * <p>Use {@link ElementTemplate#propertyGroups()} to override group labels or ordering.
   */
  String group() default "";

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

  /**
   * Constraints for the property that are used by the Modeler to validate the input. Constraints
   * can also be set using the Java Bean Validation API annotations, which takes precedence over the
   * constraints defined in this annotation.
   */
  PropertyConstraints constraints() default @PropertyConstraints;

  /** Tooltip for the property */
  String tooltip() default "";

  enum PropertyType {
    Boolean,
    Number,
    Dropdown,
    Hidden,
    String,
    Text,
    Unknown
  }

  enum DefaultValueType {
    String,
    Boolean,
    Number
  }

  @interface PropertyBinding {
    String name();
  }

  enum EqualsBoolean {
    TRUE,
    FALSE,
    NULL;

    public static EqualsBoolean fromBoolean(Boolean value) {
      if (value == null) {
        return NULL;
      } else if (value) {
        return TRUE;
      } else {
        return FALSE;
      }
    }

    public Boolean toBoolean() {
      if (this == NULL) {
        return null;
      } else {
        return this == TRUE;
      }
    }
  }

  @interface PropertyCondition {
    String property();

    String equals() default "";

    EqualsBoolean equalsBoolean() default EqualsBoolean.NULL;

    String[] oneOf() default {};

    NestedPropertyCondition[] allMatch() default {};

    boolean isActive() default false;
  }

  @interface NestedPropertyCondition {
    String property();

    /** For string properties */
    String equals() default "";

    EqualsBoolean equalsBoolean() default EqualsBoolean.NULL;

    String[] oneOf() default {};

    boolean isActive() default false;
  }

  @interface DropdownPropertyChoice {
    String value();

    String label();
  }

  @interface PropertyConstraints {
    boolean notEmpty() default false;

    int minLength() default MIN_VALUE;

    int maxLength() default MAX_VALUE;

    Pattern pattern() default @Pattern(value = "", message = "");
  }

  @interface Pattern {
    String value();

    String message();
  }
}
