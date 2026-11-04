/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_DOCUMENT_ID_COLLISION;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.document.WorkspaceDocument;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers ingested documents in a {@link DocumentWorkspace}: derives the stable id of a document
 * (see {@link DocumentHandle#idFor}) and its workspace entry.
 *
 * <p>The reference of an inline document holds its content in the workspace and in the tool result,
 * both of which are part of the job completion. An inline document whose content exceeds {@link
 * #MAX_INLINE_CONTENT_LENGTH} is therefore rejected.
 */
public final class DocumentIngestion {

  /**
   * The largest content of an inline document the workspace accepts, counted in characters of the
   * content the reference holds (base64 for binary documents, text otherwise). A larger document is
   * rejected with {@code ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE} so that the job completion, which
   * carries the content several times, stays well below the message size limit of the broker.
   */
  public static final int MAX_INLINE_CONTENT_LENGTH = 1024 * 1024;

  // the persisted reference keeps the numeric form of durations in the custom properties
  private static final ObjectMapper REFERENCE_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy()
          .registerModule(new JacksonModuleDocumentSerializer())
          .enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final TypeReference<LinkedHashMap<String, Object>> REFERENCE_TYPE =
      new TypeReference<>() {};

  private DocumentIngestion() {}

  /**
   * Returns a workspace with the given documents added; already known ids are left untouched.
   *
   * @throws ConnectorException with {@code ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE} if a new document
   *     is an inline document with more than {@link #MAX_INLINE_CONTENT_LENGTH} characters of
   *     content
   * @throws ConnectorException with {@code ERROR_CODE_DOCUMENT_ID_COLLISION} if a document gets the
   *     id of a different document that the workspace already holds. Registering the same document
   *     again is not an error.
   */
  public static DocumentWorkspace withDocuments(
      DocumentWorkspace workspace, List<IngestedDocument> documents) {
    if (documents.isEmpty()) {
      return workspace;
    }
    final var newIds = new HashSet<DocumentId>();
    final var newEntries = new ArrayList<WorkspaceDocument>();
    for (final var ingested : documents) {
      final var reference = ingested.document().reference();
      final var id = DocumentHandle.idFor(reference);
      final var known = workspace.findById(id);
      if (known.isPresent()) {
        rejectCollision(
            id,
            DocumentHandle.identityKey(referenceOf(known.get())),
            DocumentHandle.identityKey(reference));
      } else if (newIds.add(id)) {
        newEntries.add(entryFor(ingested));
      }
    }
    return workspace.withEntries(newEntries);
  }

  /**
   * Returns the agent context with the given documents registered in its document workspace. The
   * result depends on the context and the documents only, so a job that runs again computes the
   * same context.
   *
   * @throws ConnectorException with {@code ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE} if a new document
   *     is an inline document with more than {@link #MAX_INLINE_CONTENT_LENGTH} characters of
   *     content
   * @throws ConnectorException with {@code ERROR_CODE_DOCUMENT_ID_COLLISION} if a document gets the
   *     id of a different document that the workspace already holds
   */
  public static AgentContext register(AgentContext agentContext, List<IngestedDocument> documents) {
    if (documents.isEmpty()) {
      return agentContext;
    }
    return agentContext.withDocumentWorkspace(
        withDocuments(agentContext.documentWorkspace(), documents));
  }

  /**
   * Creates the workspace entry of an ingested document.
   *
   * @throws ConnectorException with {@code ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE} if the document is
   *     an inline document with more than {@link #MAX_INLINE_CONTENT_LENGTH} characters of content
   */
  public static WorkspaceDocument entryFor(IngestedDocument ingested) {
    final var document = ingested.document();
    rejectLargeInlineContent(document);
    return new WorkspaceDocument(
        DocumentHandle.idFor(document), ingested.origin(), serializeReference(document));
  }

  private static void rejectCollision(DocumentId id, List<String> knownKey, List<String> newKey) {
    if (!knownKey.equals(newKey)) {
      throw new ConnectorException(
          ERROR_CODE_DOCUMENT_ID_COLLISION,
          "The document id '%s' is shared by two different documents, so the document cannot be told apart from the one the agent already knows."
              .formatted(id));
    }
  }

  private static void rejectLargeInlineContent(Document document) {
    if (document.reference() instanceof InlineDocumentReference inline
        && inline.content() != null
        && inline.content().length() > MAX_INLINE_CONTENT_LENGTH) {
      final var name = DocumentHandle.fileNameFor(document);
      throw new ConnectorException(
          ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE,
          ("The inline document '%s' is too large: its content has %d characters, the limit is %d."
                  + " Model the process so that the file is returned as a Camunda document instead of"
                  + " inline content.")
              .formatted(
                  name != null ? name : DocumentHandle.idFor(document),
                  inline.content().length(),
                  MAX_INLINE_CONTENT_LENGTH));
    }
  }

  /**
   * Reads the stored reference of a workspace document as a typed {@link DocumentReference}, which
   * needs no {@code DocumentFactory}: it carries the identifiers and the metadata of the reference
   * but gives no access to the content.
   *
   * @throws IllegalArgumentException if the stored reference cannot be read
   */
  public static DocumentReference referenceOf(WorkspaceDocument entry) {
    return REFERENCE_MAPPER.convertValue(entry.reference(), DocumentReferenceModel.class);
  }

  private static Map<String, Object> serializeReference(Document document) {
    return REFERENCE_MAPPER.convertValue(document, REFERENCE_TYPE);
  }
}
