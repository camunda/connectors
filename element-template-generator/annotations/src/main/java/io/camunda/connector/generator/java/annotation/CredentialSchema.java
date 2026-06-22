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
 * Marks a data class as a credential schema.
 *
 * <p>A credential schema describes the field definitions a credential editor uses to render and
 * validate a credential of a given type. The {@link TemplateProperty}-annotated fields of the
 * annotated class are walked by the existing element-template generator machinery to produce the
 * embedded schema's field list.
 *
 * <p>The {@link #id()} and {@link #version()} are used as the {@code schemaRef} and {@code version}
 * of a {@code Credential} chooser property (see {@link TemplateProperty.PropertyType#Credential})
 * whose field type is this class. Reference the schema classes to embed from {@link
 * ElementTemplate#credentialSchemas()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CredentialSchema {

  /** The credential schema reference. Used as the {@code schemaRef} of a chooser property. */
  String id();

  /**
   * The credential schema version. Floor-compatibility revision used as the {@code version} of a
   * chooser property.
   */
  long version() default 1;

  /** Human-readable label of the credential schema. */
  String label() default "";
}
