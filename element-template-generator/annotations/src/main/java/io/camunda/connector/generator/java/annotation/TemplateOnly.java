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

/**
 * Marker type for a <em>template-only</em> property.
 *
 * <p>A {@code static} field of this type, annotated with {@link TemplateProperty}, declares a
 * property that appears in the generated element template but is never part of the bound connector
 * model: the runtime is responsible for resolving the corresponding value (for example, per request
 * from the activated element). The {@code static} modifier is what keeps the field out of the model
 * — it is ignored by Jackson during binding and a record exposes no accessor for it.
 *
 * <p>This type exists to make that intent explicit and type-safe at the declaration site:
 *
 * <ul>
 *   <li>It is uninstantiable, so the <em>only</em> assignable value is {@code null}. "No runtime
 *       value" is therefore enforced by the type system rather than by convention — there is no
 *       meaningful value to read even reflectively.
 *   <li>It carries no inferable property type, so the element-template-generator requires the field
 *       to declare an explicit {@link TemplateProperty#type()}. The template type is owned by the
 *       annotation, never derived from the Java field type.
 *   <li>Usages are greppable: searching for references of this type lists every template-only
 *       property in the codebase.
 * </ul>
 */
public final class TemplateOnly {

  private TemplateOnly() {
    throw new UnsupportedOperationException(
        "TemplateOnly is a marker type that holds no value and cannot be instantiated");
  }
}
