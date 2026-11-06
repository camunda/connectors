/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard.Verdict;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides for the documents whose content is asked for whether the content is added to the
 * conversation. It is the one place where a request for content meets the {@link
 * DocumentContentGuard} and the rule that content the model reads already, or that an earlier
 * request asked for, is not added again.
 */
public final class DocumentContentDelivery {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentContentDelivery.class);

  /** What happens to the content of a document that was asked for. */
  public sealed interface Outcome {

    /**
     * The content is added.
     *
     * @param withContent the block of the document with the delivery {@link
     *     DocumentDelivery#CONTENT}
     */
    record Delivered(AgentDocumentContent withContent) implements Outcome {}

    /**
     * The content is not added. The reason and its cause are logged, never put into the note.
     *
     * @param id the id of the document
     * @param note the sentence that tells the model why the content is not loaded
     */
    record Withheld(AgentDocumentId id, String note) implements Outcome {}

    /**
     * The content is not added because the model reads it already, or an earlier request of the
     * same batch dealt with it.
     *
     * @param id the id of the document
     */
    record AlreadyInConversation(AgentDocumentId id) implements Outcome {}
  }

  private final DocumentContentGuard contentGuard;

  /**
   * @param contentGuard the check of the content of a document
   */
  public DocumentContentDelivery(DocumentContentGuard contentGuard) {
    this.contentGuard = contentGuard;
  }

  /**
   * Decides for each document that was asked for, in the order of the first request, whether its
   * content is added. A document in {@code handled} is {@link Outcome.AlreadyInConversation}: the
   * model reads its content already, or an earlier request of the same batch dealt with it.
   *
   * @param known the documents the agent knows
   * @param requested the ids of the documents whose content is asked for
   * @param handled the ids of the documents whose content is not added again
   * @param documentFactory the document factory of the job, which restores the documents from the
   *     document store of the physical tenant of the job
   * @throws IllegalArgumentException if a requested id is not the one of a known document
   * @throws IllegalStateException if the document of a requested id cannot be restored
   */
  public List<Outcome> deliver(
      AgentDocuments known,
      Collection<AgentDocumentId> requested,
      Set<AgentDocumentId> handled,
      DocumentFactory documentFactory) {
    final var restorer = new DocumentRestorer(documentFactory);
    final var outcomes = new ArrayList<Outcome>();
    for (final var id : new LinkedHashSet<>(requested)) {
      if (known.find(id).isEmpty()) {
        throw new IllegalArgumentException(
            "The content of the document '%s' is asked for, but the agent does not know it"
                .formatted(id));
      }
      outcomes.add(
          handled.contains(id)
              ? new Outcome.AlreadyInConversation(id)
              : outcomeOf(known, id, restorer));
    }
    return List.copyOf(outcomes);
  }

  private Outcome outcomeOf(AgentDocuments known, AgentDocumentId id, DocumentRestorer restorer) {
    final var document =
        restorer
            .restore(known, id)
            .orElseThrow(() ->
                new IllegalStateException(
                    "The document %s cannot be restored from its reference".formatted(id)));
    return switch (contentGuard.check(document)) {
      case Verdict.Allowed ignored ->
          new Outcome.Delivered(AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT));
      case Verdict.Rejected rejected -> {
        final var rejection = rejected.rejection();
        LOGGER.debug(
            "The content of the document {} is not loaded: {}",
            id,
            rejection.modelText(),
            rejection.cause());
        yield new Outcome.Withheld(id, rejection.modelText());
      }
    };
  }
}
