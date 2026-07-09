/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.multimodal;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link ToolCallResultStrategy}: routes tool-result documents by comparing each document's
 * {@link DocumentModality} against the resolved {@link ModelCapabilities}. Routing is purely
 * capability-driven: a document is kept inline only if its {@link DocumentModality} bucket is
 * contained in {@link ModelCapabilities#toolResultModalities()}; otherwise it is stripped from the
 * {@link ToolCallResultMessage} and re-inserted, immediately after it, as a synthetic {@link
 * UserMessage} tagged {@link UserMessage#METADATA_TOOL_CALL_DOCUMENTS} (byte-identical to the
 * pre-C5 eager extraction message). A provider that cannot embed any document natively in a tool
 * result declares {@code toolResultModalities = []}, so every tool-result document falls back.
 * User/event- message documents are only validated (fail loud on unsupported), never rewritten.
 */
public class CapabilityAwareToolCallResultStrategy implements ToolCallResultStrategy {

  /** Shared with the e2e assertion helper so the fallback preamble cannot drift. */
  public static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  private record FallbackGroup(
      @Nullable String toolCallId, @Nullable String toolCallName, List<Document> documents) {}

  @Override
  public ConversationSnapshot apply(ConversationSnapshot snapshot, ModelCapabilities capabilities) {
    final var out = new ArrayList<Message>(snapshot.messages().size());
    for (Message message : snapshot.messages()) {
      switch (message) {
        case UserMessage userMessage when !isToolCallDocumentMessage(userMessage) -> {
          validateUserMessageModalities(userMessage, capabilities);
          out.add(userMessage);
        }
        case ToolCallResultMessage trm -> routeToolResult(trm, capabilities, out);
        default -> out.add(message);
      }
    }
    return new ConversationSnapshot(out, snapshot.toolDefinitions());
  }

  private void routeToolResult(
      ToolCallResultMessage trm, ModelCapabilities capabilities, List<Message> out) {
    final var supported = capabilities.toolResultModalities();
    final var newResults = new ArrayList<ToolCallResultContent>(trm.results().size());
    final var fallbackGroups = new ArrayList<FallbackGroup>();
    boolean stripped = false;

    for (ToolCallResultContent result : trm.results()) {
      final var kept = new ArrayList<Content>(result.content().size());
      final var unsupported = new ArrayList<Document>();
      for (Content content : result.content()) {
        if (content instanceof DocumentContent dc
            && !supported.contains(DocumentModality.fromDocument(dc.document()))) {
          unsupported.add(dc.document());
        } else {
          kept.add(content);
        }
      }
      if (unsupported.isEmpty()) {
        newResults.add(result);
      } else {
        stripped = true;
        newResults.add(result.withContent(kept));
        fallbackGroups.add(new FallbackGroup(result.id(), result.name(), unsupported));
      }
    }

    out.add(stripped ? trm.withResults(newResults) : trm);
    if (!fallbackGroups.isEmpty()) {
      out.add(buildSyntheticMessage(fallbackGroups));
    }
  }

  private UserMessage buildSyntheticMessage(List<FallbackGroup> groups) {
    final var content = new ArrayList<Content>();
    content.add(textContent(TOOL_CALL_DOCUMENTS_PREAMBLE));
    for (var group : groups) {
      for (var document : group.documents()) {
        content.add(
            textContent(
                DocumentReferenceXmlTag.from(document, group.toolCallId(), group.toolCallName())
                    .toXml()));
        content.add(DocumentContent.documentContent(document));
      }
    }
    final var metadata = new HashMap<String, Object>(MessageUtil.defaultMessageMetadata());
    metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    return UserMessage.builder().content(content).metadata(metadata).build();
  }

  private void validateUserMessageModalities(UserMessage message, ModelCapabilities capabilities) {
    for (Content content : message.content()) {
      if (content instanceof DocumentContent dc) {
        final var modality = DocumentModality.fromDocument(dc.document());
        if (!capabilities.userMessageModalities().contains(modality)) {
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "Document '%s' requires modality %s which the model does not support for user messages (supported: %s)."
                  .formatted(
                      dc.document().reference(), modality, capabilities.userMessageModalities()));
        }
      }
    }
  }

  private boolean isToolCallDocumentMessage(UserMessage message) {
    return message.metadata() != null
        && Boolean.TRUE.equals(message.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS));
  }
}
