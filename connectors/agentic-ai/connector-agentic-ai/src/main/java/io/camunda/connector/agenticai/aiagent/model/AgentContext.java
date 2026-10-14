/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentContext.AgentContextJacksonProxyBuilder.class)
public record AgentContext(
    // a MISSING schemaVersion (pre-versioning 8.9 data) binds to the current version by design:
    // legacy detection happens on the raw JSON tree in AgentContextSchemaMigration BEFORE
    // binding, and migrated trees are explicitly stamped to current there too, so the bound
    // object always reports the current version -- this initializer only ever fires for state
    // that truly never carried the field (e.g. constructed fresh via the builder).
    @RecordBuilder.Initializer("CURRENT_SCHEMA_VERSION") int schemaVersion,
    @RecordBuilder.Initializer("DEFAULT_STATE") AgentState state,
    @Nullable AgentMetadata metadata,
    @RecordBuilder.Initializer(source = AgentMetrics.class, value = "empty") AgentMetrics metrics,
    List<ToolDefinition> toolDefinitions,
    @Nullable ConversationContext conversation,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DocumentRegistryEntry> documents,
    // the response documents by the key the model chose, in call order; a
    // LinkedHashMap because the record builder copies a Map into an immutable map of unspecified
    // iteration order
    @RecordBuilder.Initializer(source = AgentContext.class, value = "noResponseDocuments")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        LinkedHashMap<String, ResponseDocumentSelection> responseDocuments,
    // results the agent answered itself for tool calls of the last assistant message whose other
    // calls were handed to the process; merged into the next turn input, then cleared
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ToolCallResultContent> pendingRuntimeToolCallResults,
    // the features the agent instance was created with, fixed at creation and never added later: an
    // instance without a feature keeps the behavior it started with; a LinkedHashSet because the
    // record builder copies a Set into an immutable set of unspecified iteration order
    @RecordBuilder.Initializer(source = AgentContext.class, value = "noFeatures")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        LinkedHashSet<String> features,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> properties)
    implements AgentContextBuilder.With {

  /**
   * The current persisted shape of {@code AgentContext} and its conversation content. Bumped
   * whenever a change to the persisted shape requires migrating previously-persisted state on read
   * (see {@code AgentContextSchemaMigration}).
   */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  /**
   * Marker for state whose {@code schemaVersion} field was absent (pre-versioning / Camunda 8.9).
   * Such state is missing the field entirely; it is treated as this version and migrated on read.
   */
  public static final int UNVERSIONED_SCHEMA_VERSION = 0;

  public static final AgentState DEFAULT_STATE = AgentState.INITIALIZING;

  /**
   * The feature of an agent instance that offers the runtime tools (see {@code RuntimeTool}) and
   * reserves their names against the tools of the process. An instance created without it offers no
   * runtime tool and checks no tool name against runtime tool names.
   */
  public static final String FEATURE_RUNTIME_TOOLS = "runtimeTools";

  /**
   * The feature of an agent instance that keeps the documents of its conversation in the document
   * registry and offers the tools of the agent that work with them. It implies {@link
   * #FEATURE_RUNTIME_TOOLS}. An instance created without it keeps the document handling it was
   * created with.
   */
  public static final String FEATURE_DOCUMENTS = "documents";

  public AgentContext {
    Objects.requireNonNull(state, "Agent state must not be null");
    Objects.requireNonNull(metrics, "Agent metrics must not be null");
    Objects.requireNonNull(toolDefinitions, "Tool definitions must not be null");
    Objects.requireNonNull(documents, "Documents must not be null");
    Objects.requireNonNull(responseDocuments, "Response documents must not be null");
    responseDocuments = new LinkedHashMap<>(responseDocuments);
    Objects.requireNonNull(
        pendingRuntimeToolCallResults, "Pending runtime tool call results must not be null");
    Objects.requireNonNull(features, "Features must not be null");
    features = new LinkedHashSet<>(features);
  }

  /** Returns {@code true} if the agent instance was created with the given feature. */
  public boolean supports(String feature) {
    return features.contains(feature);
  }

  /** Returns {@code true} if the agent instance was created with {@link #FEATURE_DOCUMENTS}. */
  public boolean supportsDocuments() {
    return supports(FEATURE_DOCUMENTS);
  }

  /**
   * Returns {@code true} if the agent instance was created with {@link #FEATURE_RUNTIME_TOOLS} or
   * with {@link #FEATURE_DOCUMENTS}, which implies it.
   */
  public boolean supportsRuntimeTools() {
    return supports(FEATURE_RUNTIME_TOOLS) || supportsDocuments();
  }

  /** The features of an agent instance that is created now. */
  public static LinkedHashSet<String> newInstanceFeatures() {
    return new LinkedHashSet<>(List.of(FEATURE_RUNTIME_TOOLS, FEATURE_DOCUMENTS));
  }

  /** Returns the registry of the documents the agent knows, empty if none was persisted yet. */
  public DocumentRegistry documentRegistry() {
    return DocumentRegistry.of(documents);
  }

  /** Returns the agent context carrying the given registry. */
  public AgentContext withDocumentRegistry(DocumentRegistry registry) {
    return withDocuments(registry.entries());
  }

  public AgentContext withProperty(String key, Object value) {
    final var properties = new LinkedHashMap<>(properties());
    properties.put(key, value);
    return withProperties(properties);
  }

  static LinkedHashMap<String, ResponseDocumentSelection> noResponseDocuments() {
    return new LinkedHashMap<>();
  }

  static LinkedHashSet<String> noFeatures() {
    return new LinkedHashSet<>();
  }

  public static AgentContext empty() {
    return builder().build();
  }

  public static AgentContextBuilder builder() {
    return AgentContextBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentContextJacksonProxyBuilder extends AgentContextBuilder {}
}
