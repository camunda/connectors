/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration binding for the agentic-ai framework. The {@code capabilities} map is
 * populated from two layers, deep-merged by Spring Boot:
 *
 * <ol>
 *   <li>Bundled defaults: {@code resources/capabilities/model-capabilities.yaml}, registered as a
 *       low-precedence {@link org.springframework.core.env.PropertySource} by {@link
 *       AgenticAiCapabilitiesConfiguration#setEnvironment} when the configuration bean is
 *       instantiated.
 *   <li>Application overrides: any property under {@code
 *       camunda.connector.agenticai.aiagent.framework.capabilities.*} declared by the library
 *       consumer (typically in their own {@code application.yml}). Library consumers can override
 *       an individual model's capability fields, replace a sub-modality list, or add a brand-new
 *       model entry under any api family without restating the bundled matrix.
 * </ol>
 *
 * Map keys under {@code models} carry the discriminator: keys containing {@code *} are treated as
 * glob patterns, otherwise as model ids. Optional explicit {@code id} / {@code pattern} fields
 * inside an entry override the key derivation when needed.
 *
 * <p>Capability sub-trees ({@code defaults} and per-entry {@code capabilities}) are bound to the
 * sparse {@link ModelCapabilitiesData} record so Spring Boot's relaxed binding can rebuild modality
 * lists from indexed property keys; the resolver projects them onto a flat {@link
 * ModelCapabilities} via Jackson tree merge at lookup time.
 */
@ConfigurationProperties("camunda.connector.agenticai.aiagent.framework")
public record AgenticAiFrameworkProperties(Map<String, ApiFamilyProperties> capabilities) {

  public AgenticAiFrameworkProperties {
    capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
  }

  public record ApiFamilyProperties(
      @Nullable ModelCapabilitiesData defaults, Map<String, ModelEntryProperties> models) {

    public ApiFamilyProperties {
      models = models == null ? Map.of() : Map.copyOf(models);
    }
  }

  public record ModelEntryProperties(
      @Nullable String id,
      List<String> pattern,
      List<String> aliases,
      @Nullable ModelCapabilitiesData capabilities) {

    public ModelEntryProperties {
      aliases = aliases == null ? List.of() : List.copyOf(aliases);
      pattern = pattern == null ? List.of() : List.copyOf(pattern);
    }
  }
}
