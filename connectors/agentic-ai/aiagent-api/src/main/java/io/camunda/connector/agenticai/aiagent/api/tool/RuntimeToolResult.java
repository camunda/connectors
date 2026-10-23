/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of a runtime tool call: a {@link Success} or an {@link Error}. Either way the model
 * reads {@link #content()} as the tool result; an error is plain text, since no model provider has
 * an error flag for tool results that the agent could set. A result is immutable; a {@code with}
 * method returns a changed copy.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The content is handled like the content of the result of a tool of the process. A text is
 * shown as it is. An {@link
 * io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent object} is shown as
 * JSON. A {@link io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent
 * document} of the content, also one nested in an object, is new to the agent: the agent registers
 * it, so it becomes a known document, and the model reads its tag in the place of the document,
 * never its content, store id, content hash or URL. A {@link RuntimeToolDocuments#reference
 * reference} to a document the agent knows already is shown as its tag, too.
 *
 * <h2>Effects</h2>
 *
 * <p>A {@link Success} can carry effects, which it names. The agent applies them after all calls of
 * the assistant message ran, in the order of the calls: a call neither sees nor depends on the
 * effects of another call. An {@link Error} has no effects, and a tool changes the agent in no
 * other way. The agent persists the effects with the turn; a job that fails discards them, and the
 * retry runs the call again and applies the effects again.
 *
 * <ul>
 *   <li>{@link Success#withDocumentContent}: the agent adds the content of a known document to the
 *       request that follows the tool results.
 *   <li>{@link Success#withResponseDocument}: the agent selects a known document for the final
 *       response.
 * </ul>
 *
 * <p>An effect names documents the agent knows as of the start of the batch of calls, or documents
 * this result returns itself: the id of a Camunda document is the id of the document in its store.
 * A call never names a document that another call of the same assistant message returned.
 */
@Experimental
public sealed interface RuntimeToolResult {

  /**
   * A successful result with a text as its content. A blank text gives a result without content.
   */
  static Success success(String text) {
    return new Success(ToolCallResultContent.contentFromObject(Objects.requireNonNull(text)));
  }

  /**
   * A successful result with the given blocks as its content, in the order the model reads them:
   * text, objects, documents and references to known documents.
   */
  static Success success(List<Content> content) {
    return new Success(content);
  }

  /**
   * An error result: the call failed in a way the model can act on. {@code message} is what the
   * model reads, so it says what to change.
   */
  static RuntimeToolResult error(String message) {
    return new Error(message);
  }

  /** The content of the tool result. */
  List<Content> content();

  /**
   * The documents whose content the agent adds to the request, in the order they were named; empty
   * for an {@link Error}.
   */
  default List<DocumentId> documentContent() {
    return List.of();
  }

  /**
   * The documents the agent selects for the final response, in the order they were named; empty for
   * an {@link Error}.
   */
  default List<ResponseDocumentSelection> responseDocuments() {
    return List.of();
  }

  /**
   * A successful result.
   *
   * @param content the content the model reads
   * @param documentContent the documents whose content the agent adds to the request that follows
   *     the tool results, in the order they were named
   * @param responseDocuments the documents the agent selects for the final response, in the order
   *     they were named
   */
  record Success(
      List<Content> content,
      List<DocumentId> documentContent,
      List<ResponseDocumentSelection> responseDocuments)
      implements RuntimeToolResult {

    public Success {
      content = List.copyOf(content);
      documentContent = List.copyOf(documentContent);
      responseDocuments = List.copyOf(responseDocuments);
    }

    /** A successful result without effects. */
    public Success(List<Content> content) {
      this(content, List.of(), List.of());
    }

    /**
     * Returns a copy that also asks the agent to add the content of a known document to the request
     * that follows the tool results, as if the user had sent it. The id is the one of a document in
     * {@link RuntimeToolDocuments}, or of a document this result returns: the id of a Camunda
     * document is the id of the document in its store.
     *
     * <p>The agent runs its content guard before it adds anything: when the document is not allowed
     * as content, for example because of its type or its size, it turns the whole result into an
     * error result with a fixed reason and the model gets no content. A document whose content the
     * model reads already (see {@link AgentDocument#contentInConversation()}), or that an earlier
     * call of the same assistant message asked for, is not added again; the result stays as it is.
     *
     * <p>An id of a document the agent does not know is a defect of the tool: the agent throws an
     * {@link IllegalArgumentException} and the job fails. The agent knows documents only with the
     * documents feature, so a tool that adds content is enabled for agents that have it (see {@link
     * RuntimeTool#isEnabled}).
     */
    public Success withDocumentContent(DocumentId id) {
      final var updated = new ArrayList<>(documentContent);
      updated.add(Objects.requireNonNull(id));
      return new Success(content, updated, responseDocuments);
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
     * @param key the key of the document in the response, not blank
     * @param id the id of the document
     * @param description what the response says about the document, or {@code null}
     * @throws IllegalArgumentException if the key is blank
     */
    public Success withResponseDocument(String key, DocumentId id, @Nullable String description) {
      if (Objects.requireNonNull(key).isBlank()) {
        throw new IllegalArgumentException("The key of a response document must not be blank");
      }
      final var updated = new ArrayList<>(responseDocuments);
      updated.add(new ResponseDocumentSelection(key, Objects.requireNonNull(id), description));
      return new Success(content, documentContent, updated);
    }
  }

  /** A failed call; the model reads {@code message} as text. */
  record Error(String message) implements RuntimeToolResult {

    public Error {
      Objects.requireNonNull(message);
    }

    /** The message as text content. */
    @Override
    public List<Content> content() {
      return ToolCallResultContent.contentFromObject(message);
    }
  }
}
