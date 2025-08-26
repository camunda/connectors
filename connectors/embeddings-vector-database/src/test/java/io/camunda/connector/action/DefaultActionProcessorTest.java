/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action;

import io.camunda.connector.action.embed.EmbeddingActionProcessor;
import io.camunda.connector.action.retrieve.RetrievingActionProcessor;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultActionProcessorTest {

  private final EmbeddingActionProcessor embeddingProcessor =
      Mockito.mock(EmbeddingActionProcessor.class);
  private final RetrievingActionProcessor retrievingProcessor =
      Mockito.mock(RetrievingActionProcessor.class);
  private final DocumentFactory documentFactory = Mockito.mock(DocumentFactory.class);

  @Test
  void handleEmbedRequest() {
    final var actionProcessor = new DefaultActionProcessor(embeddingProcessor, retrievingProcessor);
    final var embedRequest = EmbeddingsVectorDBRequestFixture.createDefaultEmbedOperation();

    actionProcessor.handleFlow(embedRequest, documentFactory);

    Mockito.verify(embeddingProcessor).embed(embedRequest);
  }

  @Test
  void handleRetrieveRequest() {
    final var actionProcessor = new DefaultActionProcessor(embeddingProcessor, retrievingProcessor);
    final var retrieveRequest = EmbeddingsVectorDBRequestFixture.createDefaultRetrieve();

    actionProcessor.handleFlow(retrieveRequest, documentFactory);

    Mockito.verify(retrievingProcessor).retrieve(retrieveRequest, documentFactory);
  }
}
