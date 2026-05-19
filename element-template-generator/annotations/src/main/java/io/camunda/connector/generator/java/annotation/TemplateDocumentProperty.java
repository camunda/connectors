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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Document} or {@code List<Document>} field as a document input. The element
 * template generator will produce a structured source-dropdown UI (Camunda Document / Inline
 * Content / From URL) plus a hidden FEEL composer that assembles the runtime document JSON.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface TemplateDocumentProperty {

  /**
   * Id of the hidden composer property (the property bound to the canonical document path). Set
   * this to preserve the id used by the equivalent {@code @TemplateProperty} on main, so element
   * templates that previously identified this field keep the same root id.
   */
  String id() default "";

  /** Custom binding name of the property. Defines the binding root for the generated sub-fields. */
  TemplateProperty.PropertyBinding binding() default @TemplateProperty.PropertyBinding(name = "");

  /** Description of the property. */
  String description() default "";

  /** Whether the property should be marked as optional in the element template. */
  boolean optional() default TemplateProperty.OPTIONAL_DEFAULT;

  /** Group ID for the property. All generated sub-properties inherit this group. */
  String group() default "";

  /** Condition for the property. Prepended to every generated sub-property's condition. */
  TemplateProperty.PropertyCondition condition() default
      @TemplateProperty.PropertyCondition(property = "");

  /** Tooltip for the property. */
  String tooltip() default "";

  /** Visibility of the {@code fileName} sub-property. Applies to inline and external sources. */
  FieldVisibility fileName() default FieldVisibility.OPTIONAL;

  /** Visibility of the inline source's {@code contentType} sub-property. */
  FieldVisibility contentType() default FieldVisibility.OPTIONAL;
}
