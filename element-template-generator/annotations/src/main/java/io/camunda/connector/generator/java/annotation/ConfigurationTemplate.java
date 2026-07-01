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
 * Marks a data class as a configuration template.
 *
 * <p>A configuration template describes the field definitions a configuration editor (Hub /
 * Modeler) uses to render and validate a configuration of a given type. The {@link
 * TemplateProperty}-annotated fields of the annotated class are walked by the element-template
 * generator in configuration-template extraction mode to produce the embedded template's property
 * list (with {@code property} bindings, no {@code feel}, and optional {@code secret} hints).
 *
 * <p>The {@link #id()} is used as the {@code configurationTemplate} of a chooser property (see
 * {@link TemplateProperty.PropertyType#Configuration}) whose field type is this class. The floor
 * {@link #version()}, {@link #name()}, and {@link #kind()} live only on the embedded {@code
 * configurationTemplates[]} entry, not on the chooser. Reference the template classes to embed from
 * {@link ElementTemplate#configurationTemplates()}.
 *
 * <p>"Configuration" is the generic core-modeling term; the domain concept delivered on top is the
 * <em>credential</em> — a configuration template whose {@link #kind()} is {@code CREDENTIAL}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigurationTemplate {

  /**
   * The configuration template id. Used as the {@code configurationTemplate} of a chooser property.
   */
  String id();

  /**
   * The floor version of the configuration template. The Modeler uses this as the minimum version
   * required when filtering compatible configuration instances.
   */
  long version() default 1;

  /**
   * Human-readable name of the configuration template. Shown in the configuration editor and used
   * as the primary chooser label. Required and must be non-blank (enforced at generation time) —
   * the configuration-template schema requires {@code name}.
   */
  String name();

  /**
   * The class of configuration this template produces. It is written into the created instance's
   * metadata bag, where it discriminates template-derived configurations from plain cluster
   * variables and lets the editor categorize the instance. The only value defined today is {@code
   * CREDENTIAL}.
   */
  String kind() default "CREDENTIAL";
}
