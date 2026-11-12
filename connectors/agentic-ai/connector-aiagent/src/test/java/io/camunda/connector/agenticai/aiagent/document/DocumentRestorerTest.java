/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.testutil.TestDocuments;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentRestorerTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private final DocumentRestorer restorer = new DocumentRestorer(documentFactory);

  @Test
  void restoresACamundaDocument() {
    final var document =
        documentFactory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                .fileName("a.txt")
                .build());
    final var documents =
        AgentDocuments.empty()
            .register(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));

    final var restored = restorer.restore(documents, AgentDocumentId.of(document));

    assertThat(restored)
        .hasValueSatisfying(value -> {
          assertThat(AgentDocumentId.of(value)).isEqualTo(AgentDocumentId.of(document));
          assertThat(value.asByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        });
  }

  @Test
  void isEmptyForADocumentTheAgentDoesNotKnow() {
    assertThat(restorer.restore(AgentDocuments.empty(), AgentDocumentId.of("unknown"))).isEmpty();
  }
}
