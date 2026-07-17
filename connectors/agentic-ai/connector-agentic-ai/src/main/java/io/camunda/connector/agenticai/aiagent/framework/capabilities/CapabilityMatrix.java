/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Built capability matrix used by the {@link ModelCapabilitiesResolver}. Materialised at startup
 * from {@link AgenticAiCapabilitiesProperties} (Spring Boot config) by {@link
 * CapabilityMatrixFactory}: bundled defaults from the classpath YAML are loaded as a low-precedence
 * {@link org.springframework.core.env.PropertySource} and library-consumer overrides land on top
 * before this matrix is built.
 *
 * <p>Capability sub-trees are kept as raw {@link JsonNode}s so the resolver can deep-merge
 * (Spring-Boot semantics: maps deep-merge, lists replace, scalars override) at lookup time.
 */
public record CapabilityMatrix(Map<String, ApiFamily> families) {

  public CapabilityMatrix {
    families = families == null ? Map.of() : Map.copyOf(families);
  }

  public record ApiFamily(@Nullable JsonNode defaults, List<ModelEntry> models) {
    public ApiFamily {
      models = models == null ? List.of() : List.copyOf(models);
    }
  }

  public record ModelEntry(
      @Nullable String id,
      List<String> aliases,
      List<String> patterns,
      @Nullable String backend,
      @Nullable JsonNode capabilities) {
    public ModelEntry {
      aliases = aliases == null ? List.of() : List.copyOf(aliases);
      patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }
  }
}
