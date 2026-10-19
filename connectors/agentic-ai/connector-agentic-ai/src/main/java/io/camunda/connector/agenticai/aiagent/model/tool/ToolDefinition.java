/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A tool definition, being a single tool available within the ad-hoc sub-process.
 *
 * <p>A parameter that carries a document has the string schema with the format {@code
 * camunda-document-id} (see {@link DocumentParameterSchema}) and a description that ends with the
 * hint to pass the tag of a known document, so the stored input schema holds the text the model
 * sees. The model providers see the schema without the format, which {@link
 * DocumentParameterSchema#toProviderToolDefinitions} derives where the conversation snapshot is
 * built.
 *
 * <p>{@code documentDelivery} is the optional document delivery of the documents the tool returns
 * ({@link DocumentDelivery}); absent means {@code reference}. It is part of the stored definition
 * and of the configuration fingerprint, but not of what the model providers get.
 */
@AgenticAiRecord
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(
    String name,
    @Nullable String description,
    Map<String, Object> inputSchema,
    @Nullable DocumentDelivery documentDelivery,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolDefinitionBuilder.With {

  /** Metadata key carrying the gateway type (e.g. "sandbox") for gateway-managed tools. */
  public static final String METADATA_GATEWAY_TYPE = "gatewayType";

  /** Metadata key carrying the target BPMN element id a sandbox tool call routes to. */
  public static final String METADATA_ELEMENT_ID = "elementId";

  public ToolDefinition {
    inputSchema = DocumentParameterSchema.withHint(inputSchema);
    metadata = (metadata != null) ? metadata : Map.of();
  }

  public ToolDefinition(
      String name,
      @Nullable String description,
      Map<String, Object> inputSchema,
      @Nullable DocumentDelivery documentDelivery) {
    this(name, description, inputSchema, documentDelivery, null);
  }

  @JsonIgnore
  public @Nullable String gatewayType() {
    final var value = metadata().get(METADATA_GATEWAY_TYPE);
    return value instanceof String s ? s : null;
  }

  /** Creates a tool definition with the document delivery {@code reference}. */
  public ToolDefinition(
      String name, @Nullable String description, Map<String, Object> inputSchema) {
    this(name, description, inputSchema, null, null);
  }

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
