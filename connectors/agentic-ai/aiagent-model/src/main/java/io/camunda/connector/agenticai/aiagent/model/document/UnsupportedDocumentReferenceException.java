/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * The agent is given a document that is not a Camunda document, see {@link
 * AgentDocument#isSupported(DocumentReference)}. The message is meant for the users of the process:
 * it names the kind of the document and where it came from, never its file name or its URL.
 */
public class UnsupportedDocumentReferenceException extends IllegalArgumentException {

  public UnsupportedDocumentReferenceException(String message) {
    super(message);
  }

  /** The exception for a document of the user prompt. */
  public static UnsupportedDocumentReferenceException inUserPrompt(Document document) {
    return in("the user prompt", document);
  }

  /**
   * The exception for a document of the result of a tool call.
   *
   * @param toolName the name of the tool, if the result has one
   */
  public static UnsupportedDocumentReferenceException inToolCallResult(
      @Nullable String toolName, Document document) {
    return in(
        StringUtils.isBlank(toolName)
            ? "the result of a tool"
            : "the result of the tool '%s'".formatted(toolName),
        document);
  }

  /** The exception for a document of an event. */
  public static UnsupportedDocumentReferenceException inEvent(Document document) {
    return in("an event", document);
  }

  private static UnsupportedDocumentReferenceException in(String source, Document document) {
    return new UnsupportedDocumentReferenceException(
        "Only Camunda documents can be passed to the AI Agent, but %s contains %s."
            .formatted(source, kind(document.reference())));
  }

  private static String kind(@Nullable DocumentReference reference) {
    return switch (reference) {
      case ExternalDocumentReference ignored -> "an external document";
      case InlineDocumentReference ignored -> "an inline document";
      case null, default -> "a document of an unsupported type";
    };
  }
}
