/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeToolDocumentsImplTest {

  private final Document first = createDocument("first", "first.txt");
  private final Document second = createDocument("second", "second.txt");
  private final AgentDocumentId firstId = AgentDocumentId.of(first);
  private final AgentDocumentId secondId = AgentDocumentId.of(second);
  private final AgentDocuments known =
      AgentDocuments.empty()
          .register(first, DocumentOrigin.userMessage())
          .register(second, DocumentOrigin.toolCall("search", "call-1"));

  private final RuntimeToolDocumentsImpl documents =
      new RuntimeToolDocumentsImpl(known, Set.of(firstId), TestDocuments.documentFactory());

  @Test
  void findsAndListsTheKnownDocumentsInRegistrationOrder() {
    assertThat(documents.find(firstId)).hasValue(known.find(firstId).orElseThrow());
    assertThat(documents.find(AgentDocumentId.of("unknown"))).isEmpty();
    assertThat(documents.all()).isEqualTo(known.all());
  }

  @Test
  void restoresTheLiveDocumentOfAKnownDocument() {
    assertThat(AgentDocumentId.of(documents.document(secondId))).isEqualTo(secondId);
  }

  @Test
  void anUnknownDocumentCannotBeRestored() {
    final var unknown = AgentDocumentId.of("unknown");

    assertThatThrownBy(() -> documents.document(unknown))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The agent does not know the document unknown");
  }

  @Test
  void aReferenceThatCannotBeRestoredIsAnIllegalState() {
    final var factory = mock(DocumentFactory.class);
    final var failure = new IllegalStateException("store unavailable");
    when(factory.resolve(any())).thenThrow(failure);
    final var failing = new RuntimeToolDocumentsImpl(known, Set.of(), factory);

    assertThatThrownBy(() -> failing.document(firstId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The document %s cannot be restored from its reference".formatted(firstId))
        .hasCause(failure);
  }

  @Test
  void tellsWhichDocumentsHaveTheirContentInTheConversation() {
    assertThat(documents.hasContentInConversation(firstId)).isTrue();
    assertThat(documents.hasContentInConversation(secondId)).isFalse();
    assertThat(documents.hasContentInConversation(AgentDocumentId.of("unknown"))).isFalse();
  }

  @Test
  void theReferenceOfAKnownDocumentIsItsBlockWithTheDeliveryReference() {
    assertThat(documents.reference(secondId)).isEqualTo(AgentDocumentContent.of(secondId));
  }

  @Test
  void theReferenceOfAnUnknownDocumentIsAnIllegalArgument() {
    assertThatThrownBy(() -> documents.reference(AgentDocumentId.of("unknown")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The agent does not know the document unknown");
  }
}
