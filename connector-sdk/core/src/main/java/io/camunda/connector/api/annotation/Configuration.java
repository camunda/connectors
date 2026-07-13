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
package io.camunda.connector.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a data class as a configuration type.
 *
 * <p>A configuration is a reusable value referenced by a process instead of inlining its fields
 * into the model; a credential is a configuration whose {@link #kind()} is {@code CREDENTIAL}. The
 * {@link #id()}, {@link #version()}, {@link #kind()}, and {@link #name()} identify the type and are
 * read by both the element template generator and the connector runtime.
 *
 * <p>The generator reads the {@code @TemplateProperty}-annotated fields of the class to build the
 * embedded configuration template's properties, and uses {@link #id()} as the {@code
 * configurationTemplate} of a chooser property (a field typed as this class and annotated
 * {@code @TemplateProperty(type = Configuration)}). Classes to embed are referenced from
 * {@code @ElementTemplate.configurations()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {

  /**
   * The configuration id. Used as the {@code configurationTemplate} of a chooser property and as
   * the {@code id} of the embedded configuration template.
   */
  String id();

  /**
   * Floor version of the configuration. The Modeler uses it as the minimum version when filtering
   * compatible configuration instances.
   */
  long version() default 1;

  /**
   * Display name of the configuration, shown in the configuration editor and used as the chooser
   * label. Must be non-blank.
   */
  String name();

  /**
   * The configuration class, written to the created instance's metadata bag to categorize it. The
   * only value defined today is {@code CREDENTIAL}.
   */
  String kind() default "CREDENTIAL";
}
