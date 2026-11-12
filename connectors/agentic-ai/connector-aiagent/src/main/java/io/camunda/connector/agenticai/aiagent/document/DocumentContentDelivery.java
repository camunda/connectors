/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides for the documents whose content is asked for whether the content is added to the
 * conversation. It is the one place of the rule that content the model reads already, or that an
 * earlier request of the same batch asked for, is not added again. The content of any other known
 * document is added, whatever its type and size.
 */
public class DocumentContentDelivery {

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
   * What the requests of a batch dealt with so far: the documents whose content the model reads or
   * receives in the next message.
   *
   * @param inConversation the ids of the documents whose content is not added again
   */
  public record Handled(Set<AgentDocumentId> inConversation) {

    public Handled {
      inConversation = Set.copyOf(inConversation);
    }

    /**
     * The state before the first request of a batch.
     *
     * @param idsWithContent the ids of the documents whose content the model reads as of the start
     *     of the batch
     */
    public static Handled of(Set<AgentDocumentId> idsWithContent) {
      return new Handled(idsWithContent);
    }

    private Handled after(Outcome outcome) {
      return switch (outcome) {
        case Outcome.Delivered delivered -> {
          final var ids = new HashSet<>(inConversation);
          ids.add(delivered.withContent().id());
          yield new Handled(ids);
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
   * the same batch delivers it. The content of every other document is {@link Outcome.Delivered}.
   *
   * @param known the documents the agent knows
   * @param requested the ids of the documents whose content is asked for
   * @param handled what the earlier requests of the batch dealt with
   * @throws IllegalArgumentException if a requested id is not the one of a known document
   */
  public Decision deliver(
      AgentDocuments known, Collection<AgentDocumentId> requested, Handled handled) {
    final var outcomes = new ArrayList<Outcome>();
    var state = handled;
    for (final var id : new LinkedHashSet<>(requested)) {
      if (known.find(id).isEmpty()) {
        throw new IllegalArgumentException(
            "The content of the document '%s' is asked for, but the agent does not know it"
                .formatted(id));
      }
      final var outcome =
          state.inConversation().contains(id)
              ? new Outcome.AlreadyInConversation(id)
              : new Outcome.Delivered(
                  AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT));
      outcomes.add(outcome);
      state = state.after(outcome);
    }
    return new Decision(outcomes, state);
  }
}
