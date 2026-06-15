/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.agentcoreharness;

import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessInlineFunctionConfig;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessTool;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolConfiguration;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolType;

/** Converts Camunda ToolDefinition to AWS Harness HarnessTool with inline_function type. */
public class HarnessToolConverter {

  /**
   * Converts a list of Camunda tool definitions to Harness tools.
   *
   * @param toolDefinitions the Camunda tool definitions
   * @return list of HarnessTool with inline_function configuration
   */
  public List<HarnessTool> toHarnessTools(List<ToolDefinition> toolDefinitions) {
    if (toolDefinitions == null || toolDefinitions.isEmpty()) {
      return List.of();
    }
    return toolDefinitions.stream().map(this::toHarnessTool).toList();
  }

  /**
   * Converts a single Camunda tool definition to a Harness tool.
   *
   * @param toolDefinition the Camunda tool definition
   * @return HarnessTool with inline_function configuration
   */
  public HarnessTool toHarnessTool(ToolDefinition toolDefinition) {
    var inlineFunctionConfig =
        HarnessInlineFunctionConfig.builder()
            .description(toolDefinition.description())
            .inputSchema(toDocument(toolDefinition.inputSchema()))
            .build();

    return HarnessTool.builder()
        .name(toolDefinition.name())
        .type(HarnessToolType.INLINE_FUNCTION)
        .config(HarnessToolConfiguration.fromInlineFunction(inlineFunctionConfig))
        .build();
  }

  private Document toDocument(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return Document.fromMap(Map.of());
    }
    return Document.fromMap(convertToDocumentMap(map));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Document> convertToDocumentMap(Map<String, Object> map) {
    return map.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> convertToDocument(e.getValue())));
  }

  @SuppressWarnings("unchecked")
  private Document convertToDocument(Object value) {
    if (value == null) {
      return Document.fromNull();
    } else if (value instanceof String s) {
      return Document.fromString(s);
    } else if (value instanceof Number n) {
      return Document.fromNumber(n.toString());
    } else if (value instanceof Boolean b) {
      return Document.fromBoolean(b);
    } else if (value instanceof Map<?, ?> m) {
      return Document.fromMap(convertToDocumentMap((Map<String, Object>) m));
    } else if (value instanceof List<?> l) {
      return Document.fromList(l.stream().map(this::convertToDocument).toList());
    } else {
      return Document.fromString(value.toString());
    }
  }
}
