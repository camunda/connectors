/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Creates the schemas of the parameters of a runtime tool that carry a document, so that nobody
 * writes the format that marks them by hand. Put the schema into the {@code properties} of the
 * input schema of the {@link RuntimeTool#definition() definition}.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The agent adds a sentence to the description that tells the model to pass the tag of a known
 * document, and takes the format away, before the schema goes to the model provider. The stored
 * definition keeps the schema as it is created here. The model passes a document as its {@code <doc
 * id="..." />} tag; the {@link RuntimeToolCall} holds the {@link
 * io.camunda.connector.agenticai.aiagent.model.document.DocumentId} instead.
 */
@Experimental
public final class RuntimeToolSchemas {

  private RuntimeToolSchemas() {}

  /**
   * The schema of a parameter that is always a document. The call holds a {@link
   * io.camunda.connector.agenticai.aiagent.model.document.DocumentId}; a value that is not the tag
   * of a known document answers the call with an error result before the tool runs.
   *
   * @param description what the parameter is for, shown to the model, or {@code null}
   * @return an insertion-ordered map
   */
  public static Map<String, Object> document(@Nullable String description) {
    return DocumentParameterSchema.documentSchema(description);
  }

  /**
   * The schema of a parameter that is text or a document. The call holds a {@link
   * io.camunda.connector.agenticai.aiagent.model.document.DocumentId} when the model passed exactly
   * one document tag and the text otherwise; a tag of an unknown document answers the call with an
   * error result before the tool runs.
   *
   * @param description what the parameter is for, shown to the model, or {@code null}
   * @return an insertion-ordered map
   */
  public static Map<String, Object> textOrDocument(@Nullable String description) {
    return DocumentParameterSchema.textOrDocumentSchema(description);
  }
}
