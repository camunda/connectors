/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of a runtime tool call: a {@link Success} or an {@link Error}. The model reads {@link
 * #content()} as the tool result; an error is plain text, as the tool result has no error flag.
 * {@link Experimental}, see {@link RuntimeTool}.
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
 * store id, content hash or URL. Such an instance accepts Camunda documents only: a result with an
 * external or inline document, also one nested in an object, fails the job with the error code
 * {@code UNSUPPORTED_DOCUMENT_REFERENCE}. For an instance without the feature, the model reads the
 * document reference in the tool result and the content of the document in a message after the tool
 * results, as for a tool of the process.
 */
@Experimental
public sealed interface RuntimeToolResult {

  /** A successful result with a text as its content; a blank text gives no content. */
  static RuntimeToolResult success(String text) {
    return new Success(ToolCallResultContent.contentFromObject(Objects.requireNonNull(text)));
  }

  /**
   * A successful result with the given blocks as its content, in the order the model reads them.
   */
  static RuntimeToolResult success(List<Content> content) {
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

  record Success(List<Content> content) implements RuntimeToolResult {

    public Success {
      content = List.copyOf(content);
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
