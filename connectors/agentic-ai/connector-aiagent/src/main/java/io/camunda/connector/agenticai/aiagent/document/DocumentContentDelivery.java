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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides for the documents whose content is asked for whether the content is added to the
 * conversation. It is the one place where a request for content meets the {@link
 * DocumentContentGuard} and the rule that content the model reads already, or that an earlier
 * request asked for, is not added again.
 */
public class DocumentContentDelivery {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentContentDelivery.class);

  private static final String CONTENT_LIMITS_PROPERTY =
      "camunda.connector.agenticai.aiagent.document.content-limits.<text|pdf|image>";

  private final DocumentContentGuard contentGuard;

  /**
   * @param contentGuard the check of the content of a document
   */
  public DocumentContentDelivery(DocumentContentGuard contentGuard) {
    this.contentGuard = contentGuard;
  }

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
     * same batch delivers it.
     *
     * @param id the id of the document
     */
    record AlreadyInConversation(AgentDocumentId id) implements Outcome {}
  }

  /**
   * What the requests of a batch dealt with so far: the documents whose content the model reads or
   * receives in the next message, and the documents whose content is withheld with the note for the
   * model. A document is in one of the two at most.
   *
   * @param inConversation the ids of the documents whose content is not added again
   * @param withheld the note for the model by the id of every document whose content is withheld
   */
  public record Handled(
      Set<AgentDocumentId> inConversation, Map<AgentDocumentId, String> withheld) {

    public Handled {
      inConversation = Set.copyOf(inConversation);
      withheld = Map.copyOf(withheld);
    }

    /**
     * The state before the first request of a batch.
     *
     * @param idsWithContent the ids of the documents whose content the model reads as of the start
     *     of the batch
     */
    public static Handled of(Set<AgentDocumentId> idsWithContent) {
      return new Handled(idsWithContent, Map.of());
    }

    private Handled after(Outcome outcome) {
      return switch (outcome) {
        case Outcome.Delivered delivered -> {
          final var ids = new HashSet<>(inConversation);
          ids.add(delivered.withContent().id());
          yield new Handled(ids, withheld);
        }
        case Outcome.Withheld withheldOutcome -> {
          final var notes = new HashMap<>(withheld);
          notes.put(withheldOutcome.id(), withheldOutcome.note());
          yield new Handled(inConversation, notes);
        }
        case Outcome.AlreadyInConversation ignored -> this;
      };
    }
  }

  /**
   * The outcome for every document that was asked for, and the state after the requests.
   *
   * @param outcomes the outcome per document, in the order of the first request
   * @param handled the state for the next request of the batch
   */
  public record Decision(List<Outcome> outcomes, Handled handled) {

    public Decision {
      outcomes = List.copyOf(outcomes);
    }
  }

  /**
   * Decides for each document that was asked for, in the order of the first request, whether its
   * content is added. A document that is {@link Handled#inConversation} is {@link
   * Outcome.AlreadyInConversation}: the model reads its content already, or an earlier request of
   * the same batch delivers it. A document that is {@link Handled#withheld} is {@link
   * Outcome.Withheld} again with the note it got.
   *
   * @param known the documents the agent knows
   * @param requested the ids of the documents whose content is asked for
   * @param handled what the earlier requests of the batch dealt with
   * @param documentFactory the document factory of the job, which restores the documents from the
   *     document store of the physical tenant of the job
   * @throws IllegalArgumentException if a requested id is not the one of a known document
   */
  public Decision deliver(
      AgentDocuments known,
      Collection<AgentDocumentId> requested,
      Handled handled,
      DocumentFactory documentFactory) {
    final var outcomes = new ArrayList<Outcome>();
    var state = handled;
    for (final var id : new LinkedHashSet<>(requested)) {
      final var agentDocument =
          known
              .find(id)
              .orElseThrow(() ->
                  new IllegalArgumentException(
                      "The content of the document '%s' is asked for, but the agent does not know it"
                          .formatted(id)));
      final var outcome =
          state.withheld().containsKey(id)
              ? new Outcome.Withheld(id, state.withheld().get(id))
              : state.inConversation().contains(id)
                  ? new Outcome.AlreadyInConversation(id)
                  : check(id, agentDocument.toDocument(documentFactory));
      outcomes.add(outcome);
      state = state.after(outcome);
    }
    return new Decision(outcomes, state);
  }

  private Outcome check(AgentDocumentId id, Document document) {
    return switch (contentGuard.check(document)) {
      case Verdict.Allowed ignored ->
          new Outcome.Delivered(AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT));
      case Verdict.Rejected rejected -> {
        final var rejection = rejected.rejection();
        logWithheld(id, document, rejection);
        yield new Outcome.Withheld(id, rejection.modelText());
      }
    };
  }

  private static void logWithheld(
      AgentDocumentId id, Document document, DocumentContentGuard.Rejection rejection) {
    final var metadata = document.metadata();
    final var contentType = metadata != null ? metadata.getContentType() : null;
    final var metadataSize = metadata != null ? metadata.getSize() : null;
    final var size =
        metadataSize != null && metadataSize >= 0 ? metadataSize + " bytes" : "unknown";
    switch (rejection) {
      case DocumentContentGuard.Rejection.TooLarge tooLarge ->
          LOGGER.info(
              "The content of the document {} (content type {}, size {}) is not loaded: it is above the limit of {} bytes. "
                  + "Raise the limit of its kind with the property {}.",
              id,
              contentType,
              size,
              tooLarge.limit(),
              CONTENT_LIMITS_PROPERTY);
      case DocumentContentGuard.Rejection.Unreadable unreadable ->
          LOGGER.warn(
              "The content of the document {} (content type {}, size {}) is not loaded: it cannot be read.",
              id,
              contentType,
              size,
              unreadable.cause());
      case DocumentContentGuard.Rejection.UnsupportedType unsupported ->
          LOGGER.debug(
              "The content of the document {} (content type {}, size {}) is not loaded: {}",
              id,
              contentType,
              size,
              rejection.modelText());
    }
  }
}
