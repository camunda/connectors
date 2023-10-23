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
 * Annotation that can be used to override the default sealed type hierarchy handling.
 *
 * @see TemplateDiscriminatorProperty
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TemplateSubType {

  /**
   * Custom ID of the subtype. If not specified, the ID will be derived from the subtype class name.
   * The ID is passed in the input payload and can be used by your serialization framework to
   * determine the concrete type of the object.
   *
   * <p>For example, it can be useful to define custom type IDs when your selaed hierarchy is
   * deserialized using Jackson's polymorphic deserialization feature. In this case, the type ID
   * must match the value of the {@code @JsonTypeInfo} annotation on the subtype class.
   */
  String id() default "";

  /**
   * Custom label of the discriminator property. If not specified, the label will be derived from
   * the subtype class name. The label is displayed as the name of the subtype in the discriminator
   * property dropdown.
   */
  String label() default "";

  /** Use this property to exclude the subtype from the discriminator property dropdown. */
  boolean ignore() default false;
}
