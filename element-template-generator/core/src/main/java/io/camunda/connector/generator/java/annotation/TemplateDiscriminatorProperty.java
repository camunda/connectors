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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation can be used along with {@link TemplateSubType} to override the default behavior
 * of handling sealed type hierarchies.
 *
 * <p>By default, the generator will create a property for the discriminator field that determines
 * the concrete type of the object. The property ID and label will be derived from the class name of
 * the sealed hierarchy root. Use this annotation to customize the discriminator property.
 *
 * <p>If you are customizing the subtypes using {@link TemplateSubType}, it is recommended to use
 * this annotation to customize the discriminator property as well. Auto-generated discriminator
 * property ID is not to be relied upon in manual customization.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TemplateDiscriminatorProperty {

  /**
   * Custom binding name of the discriminator property. If not specified, the name will be derived
   * from the sealed hierarchy root class name. Also defines the {@link TemplateProperty#id()} if a
   * different value is not specified.
   */
  String name();

  /**
   * Custom property ID of the discriminator property. If not specified, the {@link #name()} will be
   * used as the ID. If both are not specified, the ID will be derived from the sealed hierarchy
   * root class name.
   *
   * <p>Note that nested property prefixing applies to discriminator property ID as well. This
   * behavior can be controlled using {@link NestedProperties#addNestedPath()}.
   */
  String id() default "";

  /**
   * Custom label of the discriminator property. If not specified, the label will be derived from
   * the sealed hierarchy root class name.
   */
  String label() default "";

  /**
   * Custom group of the discriminator property. If not specified, the discriminator property will
   * be placed in the default group.
   */
  String group() default "";

  /** Custom description of the discriminator property */
  String description() default "";

  /** Pre-defined value for the discriminator property */
  String defaultValue() default "";
}
