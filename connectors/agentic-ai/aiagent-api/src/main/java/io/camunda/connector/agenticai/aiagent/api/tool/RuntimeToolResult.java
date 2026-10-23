/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of a runtime tool call: a {@link Success} or an {@link Error}. The model reads {@link
 * #content()} as the tool result; an error is plain text, as the tool result has no error flag. A
 * result is immutable; a {@code with} method returns a changed copy. {@link Experimental}, see
 * {@link RuntimeTool}.
 *
 * <p>The content is handled like the result of a tool of the process: text is shown as it is, an
 * {@link io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent object} as
 * JSON. The agent turns an object into plain JSON values (maps, lists, strings, numbers, booleans)
 * when the tool returns it, the object a later job reads from the conversation; a document in the
 * object, also one in a record, stays the document.
 *
 * <p>How the model reads a {@link
 * io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent document}, also one
 * nested in an object, depends on the agent instance. For an instance that tracks its documents
 * (one created with the {@link
 * io.camunda.connector.agenticai.aiagent.model.AgentFeatures#DOCUMENTS} feature), the document is
 * new to the agent: the agent registers it, and the model reads its handle, never its content,
 * store id, content hash or URL; a {@link RuntimeToolDocuments#reference reference} to a known document
 * is shown as its handle, too. For an instance without the feature, the model reads the document
 * reference in the tool result and the content of the document in a message after the tool results,
 * as for a tool of the process.
 *
 * <h2>Effects</h2>
 *
 * <p>A {@link Success} can carry effects. The agent applies them after all calls of the assistant
 * message ran, in call order: a call neither sees nor depends on the effects of another call. An
 * {@link Error} has no effects, and a tool changes the agent in no other way. The agent persists
 * the effects with the turn; a job that fails discards them, and the retry applies them again.
 *
 * <ul>
 *   <li>{@link Success#withDocumentContent}: the agent adds the content of a known document to the
 *       request that follows the tool results.
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
   * text, objects, documents and references to known documents.
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

  /** The documents whose content the agent adds to the request, in order; empty for an error. */
  default List<DocumentId> documentContent() {
    return List.of();
  }

  record Success(List<Content> content, List<DocumentId> documentContent)
      implements RuntimeToolResult {

    public Success {
      content = List.copyOf(content);
      documentContent = List.copyOf(documentContent);
    }

    /** A successful result without effects. */
    public Success(List<Content> content) {
      this(content, List.of());
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
