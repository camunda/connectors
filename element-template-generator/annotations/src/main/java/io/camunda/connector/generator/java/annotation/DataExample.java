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

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface DataExample {

  /**
   * Id an example can be explicitly marked with to designate it as the canonical one (e.g. for
   * generated help tooltips), when a class declares more than one {@code @DataExample}. Opt-in
   * only: {@link #id()}'s own default stays {@code ""} for backward compatibility with existing
   * consumers of this annotation that key their own documentation lookups by id.
   */
  String DEFAULT_ID = "default";

  /**
   * @return ID of the example which can be used when generating documentation. Connectors with
   *     multiple examples should give each a distinct, stable id; mark one {@link #DEFAULT_ID} to
   *     designate it as canonical.
   */
  String id() default "";

  /**
   * @return FEEL expression that will be evaluated against the result of the annotated method.
   */
  String feel() default "";
}
