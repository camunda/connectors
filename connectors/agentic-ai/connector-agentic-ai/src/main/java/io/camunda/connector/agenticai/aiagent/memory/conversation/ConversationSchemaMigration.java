/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.document.jackson.deserializer.DeserializationUtil;
import java.io.UncheckedIOException;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shared upcaster for tool-call-result content persisted before {@link
 * AgentContext#CURRENT_SCHEMA_VERSION} (Camunda 8.9): lifts the old flat {@code content} value into
 * the current structured {@link Content} list shape.
 *
 * <p>Operates on raw {@link JsonNode} trees so the transform runs before object binding, at every
 * persisted root that carries tool-call-result content: the in-process {@code agentContext} process
 * variable (via {@link #migrateAndBindAgentContext}), the Camunda document store payload, and AWS
 * AgentCore blob envelopes (both via {@link #upcastMessages}/{@link #upcastToolCallResults}).
 */
public final class ConversationSchemaMigration {

  private static final String FIELD_SCHEMA_VERSION = "schemaVersion";
  private static final String FIELD_CONVERSATION = "conversation";
  private static final String FIELD_MESSAGES = "messages";
  private static final String FIELD_RESULTS = "results";
  private static final String FIELD_CONTENT = "content";
  private static final String FIELD_ROLE = "role";
  private static final String ROLE_TOOL_CALL_RESULT = "tool_call_result";

  private ConversationSchemaMigration() {}

  /**
   * Reads an {@code agentContext} JSON tree, upcasts its in-process conversation messages if the
   * tree was persisted before {@link AgentContext#CURRENT_SCHEMA_VERSION}, then binds the (possibly
   * upcasted) tree via {@code AgentContext}'s builder deserializer.
   *
   * <p>Shared by the request-field deserializer ({@code VersionedAgentContextDeserializer}) and the
   * backward-compatibility tests, so both exercise the same migration path. Binding via {@code
   * mapper.treeToValue(..., AgentContext.class)} goes through {@code AgentContext}'s own
   * {@code @JsonDeserialize} builder, not back through a field-level deserializer, so this does not
   * recurse.
   */
  public static @Nullable AgentContext migrateAndBindAgentContext(
      @Nullable JsonNode agentContextTree, ObjectMapper mapper) throws JsonProcessingException {
    if (agentContextTree == null || agentContextTree.isNull() || agentContextTree.isMissingNode()) {
      return null;
    }

    if (agentContextTree.isObject()) {
      int schemaVersion = schemaVersionOf(agentContextTree);
      rejectIfNewerThanSupported(schemaVersion);

      if (schemaVersion < AgentContext.CURRENT_SCHEMA_VERSION) {
        JsonNode conversation = agentContextTree.get(FIELD_CONVERSATION);
        if (conversation != null
            && conversation.isObject()
            && conversation.hasNonNull(FIELD_MESSAGES)) {
          upcastMessages(conversation.get(FIELD_MESSAGES), mapper);
        }
      }
    }

    return mapper.treeToValue(agentContextTree, AgentContext.class);
  }

  /**
   * Rejects a persisted {@code agentContext} schema version newer than {@link
   * AgentContext#CURRENT_SCHEMA_VERSION}. A rolled-back or older connector runtime reading state
   * written by a newer one must fail loud rather than silently binding a shape it doesn't fully
   * understand and rewriting it under a lower version.
   */
  private static void rejectIfNewerThanSupported(int schemaVersion) {
    if (schemaVersion > AgentContext.CURRENT_SCHEMA_VERSION) {
      throw new IllegalStateException(
          "Persisted conversation schema version %d is newer than the highest version supported by this connector (%d). This state was written by a newer connector version; rolling back to an older connector version is not supported. Upgrade the connector runtime to the version that wrote this state."
              .formatted(schemaVersion, AgentContext.CURRENT_SCHEMA_VERSION));
    }
  }

  private static int schemaVersionOf(JsonNode agentContextTree) {
    return agentContextTree.hasNonNull(FIELD_SCHEMA_VERSION)
        ? agentContextTree.get(FIELD_SCHEMA_VERSION).asInt()
        : AgentContext.LEGACY_SCHEMA_VERSION;
  }

  /**
   * Upcasts a persisted messages array in place: for each {@code tool_call_result} message, lifts
   * every {@code results[*].content} legacy node into the current {@code List<Content>} shape.
   * Guards defensively against non-array/non-object shapes; a pointer-style conversation (document
   * or AgentCore) has no {@code messages} node and is left untouched by the caller.
   */
  public static void upcastMessages(@Nullable JsonNode messagesNode, ObjectMapper mapper) {
    if (messagesNode == null || !messagesNode.isArray()) {
      return;
    }

    for (JsonNode message : messagesNode) {
      if (message.isObject() && ROLE_TOOL_CALL_RESULT.equals(message.path(FIELD_ROLE).asText())) {
        upcastToolCallResults(message.get(FIELD_RESULTS), mapper);
      }
    }
  }

  /**
   * Upcasts a persisted tool-call-results array in place (the AWS AgentCore blob path, which has no
   * wrapping {@code tool_call_result} message envelope).
   */
  public static void upcastToolCallResults(@Nullable JsonNode resultsNode, ObjectMapper mapper) {
    if (resultsNode == null || !resultsNode.isArray()) {
      return;
    }

    for (JsonNode result : resultsNode) {
      if (result instanceof ObjectNode resultObject && resultObject.has(FIELD_CONTENT)) {
        List<Content> lifted = liftLegacyContent(resultObject.get(FIELD_CONTENT), mapper);
        resultObject.set(FIELD_CONTENT, contentListToTree(lifted, mapper));
      }
    }
  }

  /**
   * Renders a {@code List<Content>} back to a JSON tree with each element's {@code type}
   * discriminator included. {@code ObjectMapper#valueToTree} alone is insufficient here: it
   * resolves the serializer from the argument's runtime class, and a bare {@code TextContent}
   * (etc.) instance carries no {@code @JsonTypeInfo} of its own — only the {@link Content}
   * interface does. Writing through an {@link ObjectMapper#writerFor(JavaType)} bound to the
   * declared {@code List<Content>} type forces Jackson to resolve the polymorphic serializer the
   * same way it would for a normal {@code List<Content>}-typed bean property.
   */
  private static JsonNode contentListToTree(List<Content> content, ObjectMapper mapper) {
    try {
      JavaType listOfContentType =
          mapper.getTypeFactory().constructCollectionType(List.class, Content.class);
      return mapper.readTree(mapper.writerFor(listOfContentType).writeValueAsString(content));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Lifts a single legacy {@code content} node into the current {@code List<Content>} shape,
   * mirroring exactly the semantics of the pre-existing (now removed) {@code
   * ToolCallResultContent.ContentJsonDeserializer#flatLegacyContent}:
   *
   * <ul>
   *   <li>{@code null}/missing -&gt; {@code []}
   *   <li>blank textual -&gt; {@code []}
   *   <li>non-blank textual -&gt; a single {@link TextContent}
   *   <li>a document reference -&gt; a single {@link DocumentContent}
   *   <li>anything else (object, array — including a legacy gateway {@code List<McpContent>} whose
   *       elements happen to share {@code text}/{@code object}/{@code document} type discriminators
   *       with domain {@link Content} — number, or boolean) -&gt; a single {@link ObjectContent}
   *       wrapping the whole node, opaque and unsplit
   * </ul>
   */
  static List<Content> liftLegacyContent(@Nullable JsonNode node, ObjectMapper mapper) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return List.of();
    }

    if (node.isTextual()) {
      String text = node.textValue();
      return (text == null || text.isBlank()) ? List.of() : List.of(TextContent.textContent(text));
    }

    try {
      if (DeserializationUtil.isDocumentReference(node)) {
        Document document = mapper.treeToValue(node, Document.class);
        return List.of(DocumentContent.documentContent(document));
      }

      // any other object, or an array of untyped values (or of overlapping-but-legacy typed
      // values, e.g. a gateway tool's List<McpContent>), or a number/boolean — deserialize to its
      // natural Java type and wrap it as a single opaque content block rather than inspecting it
      // further (routes through the connectors document module's Object deserializer, resolving
      // any nested document references/intrinsic functions)
      Object value = mapper.treeToValue(node, Object.class);
      return List.of(ObjectContent.objectContent(value));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
