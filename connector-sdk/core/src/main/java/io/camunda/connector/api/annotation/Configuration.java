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
 * <p>A configuration is a reusable, org-managed value (a credential is a configuration whose {@link
 * #kind()} is {@code CREDENTIAL}) that a process references instead of inlining the fields into the
 * model. This annotation is the identity contract of that type: its {@link #id()}, {@link #kind()},
 * and {@link #version()} identify the configuration and are read both by the element template
 * generator (to emit the chooser and the embedded configuration template) and by the connector
 * runtime (to interpret a bound {@code configuration} object).
 *
 * <p>It lives in the SDK — rather than in the element-template-generator annotations — precisely
 * because it is a runtime-readable contract, in the same category as {@code @OutboundConnector} /
 * {@code @InboundConnector}: a type-level identity annotation consumed by both the runtime and the
 * generator. Presentation-only annotations (such as {@code @TemplateProperty} and
 * {@code @ElementTemplate}) remain in the generator.
 *
 * <p>The generator walks the {@code @TemplateProperty}-annotated fields of the annotated class in
 * configuration-template extraction mode to produce the embedded template's property list (with
 * {@code property} bindings, no {@code feel}, and optional {@code secret} hints). The {@link #id()}
 * is used as the {@code configurationTemplate} of a chooser property (a field typed as this class
 * and annotated {@code @TemplateProperty(type = Configuration)}). The floor {@link #version()},
 * {@link #name()}, and {@link #kind()} live only on the embedded {@code configurationTemplates[]}
 * entry, not on the chooser. Reference the classes to embed from
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
   * The floor version of the configuration. The Modeler uses this as the minimum version required
   * when filtering compatible configuration instances.
   */
  long version() default 1;

  /**
   * Human-readable name of the configuration. Shown in the configuration editor and used as the
   * primary chooser label. Required and must be non-blank (enforced at generation time) — the
   * configuration-template schema requires {@code name}.
   */
  String name();

  /**
   * The class of configuration this type produces. It is written into the created instance's
   * metadata bag, where it discriminates configuration-derived instances from plain cluster
   * variables and lets the editor categorize the instance. The only value defined today is {@code
   * CREDENTIAL}.
   */
  String kind() default "CREDENTIAL";
}
