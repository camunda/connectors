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

@Target(value = {ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface InboundConnector {
  /** Name of the connector */
  String name();

  /** Type the connector registers for */
  String type();

  /**
   * Names of the properties that are taken into account for connector deduplication. If empty, all
   * properties are taken into account.
   *
   * @deprecated use {@link #deduplicationClasses()} instead — it derives the deduplication scope
   *     from a data class rather than an error-prone, hand-maintained list of property names.
   */
  @Deprecated
  String[] deduplicationProperties() default {};

  /**
   * Data classes whose properties define the connector's deduplication scope. When non-empty, only
   * the properties contributed by these classes are taken into account for deduplication; any other
   * bound properties (for example, element-scoped ones that a connector merges into the same
   * element template via {@code @ElementTemplate(inputDataClass = ...)}) are excluded.
   *
   * <p>If empty, all properties are taken into account (the default). This is the class-based
   * counterpart of {@link #deduplicationProperties()} and is preferred over it.
   */
  Class<?>[] deduplicationClasses() default {};
}
