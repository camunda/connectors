/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import org.junit.jupiter.api.Test;

class RuntimeToolDocumentsImplTest {

  private final Document first = createDocument("first", "first.txt");
  private final Document second = createDocument("second", "second.txt");
  private final AgentDocumentId firstId = AgentDocumentId.of(first);
  private final AgentDocumentId secondId = AgentDocumentId.of(second);
  private final AgentDocuments known =
      AgentDocuments.empty()
          .register(first, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(second, AgentDocumentOrigin.toolCall("search", "call-1"));

  private final RuntimeToolDocumentsImpl documents = new RuntimeToolDocumentsImpl(known);

  @Test
  void findsTheKnownDocumentsAndNoOtherOne() {
    assertThat(documents.find(firstId)).hasValue(known.find(firstId).orElseThrow());
    assertThat(documents.find(secondId)).hasValue(known.find(secondId).orElseThrow());
    assertThat(documents.find(AgentDocumentId.of("unknown"))).isEmpty();
  }
}
