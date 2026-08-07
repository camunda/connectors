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
package io.camunda.connector.generator.java.util;

import io.camunda.connector.generator.dsl.ConfigurationTemplate;
import io.camunda.connector.generator.dsl.PropertyBuilder;

/**
 * Builds the {@link ConfigurationTemplate} wrapper (id/kind/version/name plus embedded properties)
 * for a single {@code @Configuration}-annotated class.
 *
 * <p>Shared by the annotation-driven path ({@code
 * io.camunda.connector.generator.java.ClassBasedTemplateGenerator}, via
 * {@code @ElementTemplate.configurations()}) and any DSL that embeds a configuration template
 * directly (e.g. the HTTP DSL's authentication credential chooser), so both paths are structurally
 * guaranteed to emit identical output for the same class instead of relying on the two
 * implementations being kept in sync by hand.
 */
public class ConfigurationTemplateUtil {

  private ConfigurationTemplateUtil() {}

  /**
   * @param templateClass a class annotated with {@code @Configuration}; its {@code
   *     TemplateProperty}-annotated fields become the embedded template's properties, and its
   *     {@code id}, {@code kind}, {@code version}, and {@code name} become the template's
   *     corresponding fields.
   * @param context the generation context (outbound/inbound) used to resolve property types
   * @return the resulting {@link ConfigurationTemplate}
   * @throws IllegalArgumentException if {@code templateClass} is not annotated with
   *     {@code @Configuration}, or if the annotation's {@code name} or {@code kind} is blank
   */
  public static ConfigurationTemplate fromAnnotatedClass(
      Class<?> templateClass, TemplateGenerationContext context) {
    var configurationAnnotation =
        templateClass.getAnnotation(io.camunda.connector.api.annotation.Configuration.class);
    if (configurationAnnotation == null) {
      throw new IllegalArgumentException(
          "Class "
              + templateClass.getName()
              + " referenced in @ElementTemplate.configurations() must be"
              + " annotated with @Configuration");
    }
    if (configurationAnnotation.name().isBlank()) {
      throw new IllegalArgumentException(
          "@Configuration on "
              + templateClass.getName()
              + " must declare a non-blank name (generator constraint; the schema requires"
              + " name to be present but does not itself enforce non-blank)");
    }
    if (configurationAnnotation.kind().isBlank()) {
      throw new IllegalArgumentException(
          "@Configuration on "
              + templateClass.getName()
              + " must declare a non-blank kind (required by the configuration-template"
              + " schema)");
    }
    var templateProperties =
        TemplatePropertiesUtil.extractConfigurationTemplatePropertiesFromType(
                templateClass, context)
            .stream()
            .map(PropertyBuilder::build)
            .toList();
    return new ConfigurationTemplate(
        configurationAnnotation.id(),
        configurationAnnotation.kind(),
        configurationAnnotation.version(),
        configurationAnnotation.name(),
        templateProperties);
  }
}
