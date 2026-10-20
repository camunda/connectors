/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the engine-pinned linked system prompt resource of an activated job. Mirrors the logic
 * of {@link AgentTaskV2Function} for the AI Agent Sub-process (epic 75 prototype, unsupported).
 */
public class LinkedSystemPromptResolver {
  private static final String LINKED_RESOURCES_HEADER = "linkedResources";
  private static final String SYSTEM_PROMPT_RESOURCE_TYPE = "system-prompt";
  private static final String SYSTEM_PROMPT_LINK_NAME = "systemPrompt";
  private static final String SYSTEM_PROMPT_BINDING_HEADER = "systemPromptBinding";
  private static final String ERROR_CODE = "LINKED_SYSTEM_PROMPT_RESOLUTION_ERROR";

  private final CamundaClient camundaClient;
  private final ObjectMapper objectMapper;

  public LinkedSystemPromptResolver(CamundaClient camundaClient, ObjectMapper objectMapper) {
    this.camundaClient = camundaClient;
    this.objectMapper = objectMapper;
  }

  public static String compose(@Nullable String inline, ResolvedLinkedSystemPrompt linked) {
    if (StringUtils.isBlank(linked.prompt())) {
      return StringUtils.defaultString(inline);
    }
    return StringUtils.isBlank(inline) ? linked.prompt() : inline + "\n\n" + linked.prompt();
  }

  public @Nullable ResolvedLinkedSystemPrompt resolve(Map<String, String> customHeaders) {
    var raw = customHeaders.get(LINKED_RESOURCES_HEADER);
    if (StringUtils.isBlank(raw)) {
      return null;
    }
    final List<LinkedResource> resources;
    try {
      resources = objectMapper.readValue(raw, new TypeReference<List<LinkedResource>>() {});
    } catch (JsonProcessingException e) {
      throw new ConnectorException(ERROR_CODE, "Failed to parse linked resource metadata.", e);
    }
    var matches =
        resources.stream()
            .filter(
                r ->
                    SYSTEM_PROMPT_RESOURCE_TYPE.equals(r.resourceType())
                        && SYSTEM_PROMPT_LINK_NAME.equals(r.linkName()))
            .toList();
    if (matches.isEmpty()) {
      return null;
    }
    if (matches.size() > 1) {
      throw new ConnectorException(ERROR_CODE, "Multiple linked system prompt resources.");
    }
    var resourceKey = matches.getFirst().resourceKey();
    if (resourceKey == null) {
      throw new ConnectorException(ERROR_CODE, "The linked system prompt has no resource key.");
    }
    var binding = customHeaders.get(SYSTEM_PROMPT_BINDING_HEADER);
    if (StringUtils.isBlank(binding)) {
      throw new ConnectorException(ERROR_CODE, "The linked system prompt has no binding metadata.");
    }
    try {
      var resource = camundaClient.newResourceGetRequest(resourceKey).execute();
      var prompt = camundaClient.newResourceContentBinaryGetRequest(resourceKey).execute();
      return new ResolvedLinkedSystemPrompt(
          prompt, resource.getResourceId(), binding, resource.getVersion(), resourceKey);
    } catch (Exception e) {
      throw new ConnectorException(
          ERROR_CODE,
          "Failed to retrieve linked system prompt resource %d.".formatted(resourceKey),
          e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LinkedResource(@Nullable Long resourceKey, String resourceType, String linkName) {}

  public record ResolvedLinkedSystemPrompt(
      String prompt, String promptId, String binding, int version, long resourceKey) {}
}
