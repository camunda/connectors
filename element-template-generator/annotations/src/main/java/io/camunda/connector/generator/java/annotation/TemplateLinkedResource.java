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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@code zeebe:linkedResource} block for the annotated request class. Placing this on a
 * connector input record causes the element-template generator to emit three properties for the
 * linked resource: a hidden {@code resourceType} marker, a {@code bindingType} dropdown, and a
 * {@code resourceId} string field.
 *
 * <p>This annotation is repeatable via {@link TemplateLinkedResources}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(TemplateLinkedResources.class)
public @interface TemplateLinkedResource {

  /**
   * Symbolic name that ties the three generated properties together, e.g. {@code formDefinition}.
   */
  String linkName();

  /** Value written to the hidden {@code resourceType} property, e.g. {@code form}. */
  String resourceType();

  /** Property group for the visible fields (bindingType dropdown and resourceId input). */
  String group() default "";

  /** Label for the resource ID input. Defaults to {@code "Form ID"} if blank. */
  String resourceIdLabel() default "";

  /** Description for the resource ID input. Omitted if blank. */
  String resourceIdDescription() default "";

  /** Label for the binding-type dropdown. Defaults to {@code "Form binding"} if blank. */
  String bindingTypeLabel() default "";
}
