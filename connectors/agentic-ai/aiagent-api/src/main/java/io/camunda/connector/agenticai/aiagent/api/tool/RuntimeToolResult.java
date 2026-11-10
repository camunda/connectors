/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of a runtime tool call: a {@link Success} or an {@link Error}. The model reads {@link
 * #content()} as the tool result. An error is plain text. A result is immutable: a {@code with}
 * method returns a changed copy. {@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The agent shows text as it is and an {@link
 * io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent object} as JSON. It
 * turns an object into plain JSON values when the tool returns it. A document in the object stays a
 * document.
 *
 * <h2>Documents</h2>
 *
 * <p>A result holds a document as the {@link Document} of the connector SDK, also in an object. For
 * an agent instance with the {@link
 * io.camunda.connector.agenticai.aiagent.model.AgentFeatures#DOCUMENTS} feature, the agent
 * registers the document and the model reads its handle. Such an instance accepts Camunda documents
 * only: an external or inline document fails the job with {@code UNSUPPORTED_DOCUMENT_REFERENCE}.
 * For an instance without the feature, the model reads the document as for a tool of the process.
 * An {@code AgentDocument} or an {@code AgentDocumentContent} block in a result fails the job.
 *
 * <h2>Effects</h2>
 *
 * <p>A {@link Success} can carry effects. The agent applies them in call order after all calls of
 * the assistant message ran. An {@link Error} has no effects. A failed job discards the effects,
 * and the retry applies them again.
 *
 * <ul>
 *   <li>{@link Success#withDocumentContent}: adds the content of a document to the next request.
 *   <li>{@link Success#withResponseDocument}: selects a document for the final response.
 * </ul>
 */
@Experimental
public sealed interface RuntimeToolResult {

  /** A successful result with a text as its content; a blank text gives no content. */
  static Success success(String text) {
    return new Success(ToolCallResultContent.contentFromObject(Objects.requireNonNull(text)));
  }

  /**
   * A successful result with the given blocks as its content, in the order the model reads them:
   * text, objects and documents.
   */
  static Success success(List<Content> content) {
    return new Success(content);
  }

  /**
   * An error result for a failure the model can act on. {@code message} is what the model reads, so
   * it says what to change; a blank message gives the text "The tool failed.".
   */
  static RuntimeToolResult error(String message) {
    return new Error(message);
  }

  List<Content> content();

  /**
   * A successful result.
   *
   * @param content the content the model reads as the tool result
   * @param effects what the agent does besides, in the order they were added
   */
  record Success(List<Content> content, List<ToolCallEffect> effects) implements RuntimeToolResult {

    public Success {
      content = List.copyOf(content);
      effects = List.copyOf(effects);
    }

    /** A successful result without effects. */
    public Success(List<Content> content) {
      this(content, List.of());
    }

    /**
     * Returns a copy that asks the agent to add the content of a document to the next request.
     *
     * <p>{@code id} identifies a document that the agent knows when the batch starts, or a document
     * that this result returns ({@link AgentDocumentId#of(Document)}). The agent adds the content
     * once and tells the model in the result. An unknown id fails the job.
     */
    public Success withDocumentContent(AgentDocumentId id) {
      return withEffect(new ToolCallEffect.AddDocumentContent(Objects.requireNonNull(id)));
    }

    /**
     * Returns a copy that also asks the agent to select a known document for the final response
     * under {@code key}, which is the key the process finds the document by. Every final response
     * of the agent instance carries all selected documents. A selection under a key that is already
     * used, by this result, an earlier call or an earlier turn, replaces the earlier selection and
     * keeps its position.
     *
     * <p>The id is the one of a document in {@link RuntimeToolDocuments}, or of a document this
     * result returns. An id of a document the agent does not know is a defect of the tool: the
     * agent throws an {@link IllegalArgumentException} and the job fails. The agent knows documents
     * only with the documents feature, so a tool that selects response documents is enabled for
     * agents that have it (see {@link RuntimeTool#isEnabled}).
     *
     * @param key the key of the document in the response, see {@link
     *     ResponseDocumentSelection#KEY_PATTERN}
     * @param id the id of the document
     * @param description what the response says about the document, or {@code null}
     * @throws IllegalArgumentException if the key does not match the pattern
     */
    public Success withResponseDocument(
        String key, AgentDocumentId id, @Nullable String description) {
      return withEffect(
          new ToolCallEffect.SelectResponseDocument(
              new ResponseDocumentSelection(key, Objects.requireNonNull(id), description)));
    }

    private Success withEffect(ToolCallEffect effect) {
      final var updated = new ArrayList<>(effects);
      updated.add(effect);
      return new Success(content, updated);
    }
  }

  // the SPI names the variants Success and Error; callers use RuntimeToolResult.Error
  @SuppressWarnings("AvoidCommonTypeNames")
  record Error(String message) implements RuntimeToolResult {

    private static final String BLANK_MESSAGE = "The tool failed.";

    public Error {
      Objects.requireNonNull(message);
    }

    @Override
    public List<Content> content() {
      return ToolCallResultContent.contentFromObject(message.isBlank() ? BLANK_MESSAGE : message);
    }
  }
}
