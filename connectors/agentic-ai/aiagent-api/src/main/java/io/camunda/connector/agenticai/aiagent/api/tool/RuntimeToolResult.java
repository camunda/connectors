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
 * The outcome of a runtime tool call: a {@link Success} or an {@link Error}. Either way the model
 * reads {@link #content()} as the tool result; an error is plain text, since no model provider has
 * an error flag for tool results that the agent could set. A result is immutable.
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
 */
@Experimental
public sealed interface RuntimeToolResult {

  /**
   * A successful result with a text as its content. A blank text gives a result without content.
   */
  static RuntimeToolResult success(String text) {
    return new Success(ToolCallResultContent.contentFromObject(Objects.requireNonNull(text)));
  }

  /**
   * A successful result with the given blocks as its content, in the order the model reads them:
   * text, objects, documents and references to known documents.
   */
  static RuntimeToolResult success(List<Content> content) {
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

  /** A successful result. */
  record Success(List<Content> content) implements RuntimeToolResult {

    public Success {
      content = List.copyOf(content);
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
