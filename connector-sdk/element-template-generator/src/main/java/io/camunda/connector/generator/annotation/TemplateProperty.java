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

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TemplateProperty {

  String name() default "";

  String label() default "";

  String description() default "";

  boolean optional() default false;

  PropertyType type() default PropertyType.Unknown;

  String[] choices() default {};

  FeelMode feel() default FeelMode.disabled;

  String defaultValue() default "";

  String group() default "";

  enum PropertyType {
    Boolean,
    Dropdown,
    Hidden,
    String,
    Text,
    Unknown
  }
}
