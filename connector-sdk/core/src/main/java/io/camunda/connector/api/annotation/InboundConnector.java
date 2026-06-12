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
   * @deprecated since 8.10.0, scheduled for removal. This inclusion allow-list names the only
   *     properties that contribute to the deduplication ID, which silently leaves every other
   *     <em>bound</em> property out of the deduplication scope while it remains bound to the shared
   *     executable model. When several elements share a deduplication ID but differ in such a
   *     property, the value retained by the deduplicated executable is arbitrary (whichever element
   *     won the grouping). Instead, rely on the default deduplication scope — all bound properties
   *     except the runtime-managed ones — and declare any property that must not influence
   *     deduplication as a <em>template-only</em> property (see {@code TemplateOnly}), resolving
   *     its value per request at runtime rather than binding it to the model. See issue <a
   *     href="https://github.com/camunda/connectors/issues/6684">#6684</a>.
   */
  @Deprecated(since = "8.10.0", forRemoval = true)
  String[] deduplicationProperties() default {};
}
