/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The documents whose content the model reads or receives in the next message, as far as the
 * requests of a batch dealt with them. It decides for the documents whose content is asked for
 * whether the content is added to the conversation, and returns the delivery for the next request.
 * Content that the model reads already, or that an earlier request of the same batch asked for, is
 * not added again. The content of any other document is added, whatever its type and size.
 *
 * @param inConversation the ids of the documents whose content is not added again
 */
public record DocumentContentDelivery(Set<AgentDocumentId> inConversation) {

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
     * The content is not added because the model reads it already, or an earlier request of the
     * same batch delivers it.
     *
     * @param id the id of the document
     */
    record AlreadyInConversation(AgentDocumentId id) implements Outcome {}
  }

  /**
   * The outcome for every document that was asked for, and the delivery after the requests.
   *
   * @param outcomes the outcome per document, in the order of the first request
   * @param next the delivery for the next request of the batch
   */
  public record Decision(List<Outcome> outcomes, DocumentContentDelivery next) {

    public Decision {
      outcomes = List.copyOf(outcomes);
    }
  }

  public DocumentContentDelivery {
    inConversation = Set.copyOf(inConversation);
  }

  /**
   * The delivery before the first request of a batch.
   *
   * @param idsWithContent the ids of the documents whose content the model reads as of the start of
   *     the batch
   */
  public static DocumentContentDelivery of(Set<AgentDocumentId> idsWithContent) {
    return new DocumentContentDelivery(idsWithContent);
  }

  /**
   * Decides for each document that was asked for, in the order of the first request, whether its
   * content is added. A document that is {@link #inConversation} is {@link
   * Outcome.AlreadyInConversation}: the model reads its content already, or an earlier request of
   * the same batch delivers it. The content of every other document is {@link Outcome.Delivered}.
   *
   * <p>The ids must be the ones of documents the agent knows.
   *
   * @param requested the ids of the documents whose content is asked for
   */
  public Decision deliver(Collection<AgentDocumentId> requested) {
    final var outcomes = new ArrayList<Outcome>();
    var delivery = this;
    for (final var id : new LinkedHashSet<>(requested)) {
      final var outcome =
          delivery.inConversation.contains(id)
              ? new Outcome.AlreadyInConversation(id)
              : new Outcome.Delivered(
                  AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT));
      outcomes.add(outcome);
      delivery = delivery.after(outcome);
    }
    return new Decision(outcomes, delivery);
  }

  private DocumentContentDelivery after(Outcome outcome) {
    return switch (outcome) {
      case Outcome.Delivered delivered -> {
        final var ids = new HashSet<>(inConversation);
        ids.add(delivered.withContent().id());
        yield new DocumentContentDelivery(ids);
      }
      case Outcome.AlreadyInConversation ignored -> this;
    };
  }
}
