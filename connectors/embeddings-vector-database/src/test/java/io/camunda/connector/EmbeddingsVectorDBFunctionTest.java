/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture.createDefaultRetrieve;

import io.camunda.connector.action.DefaultActionProcessor;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EmbeddingsVectorDBFunctionTest {

  @Test
  void executeSuccess() {
    final var ctx = Mockito.mock(OutboundConnectorContext.class);
    final var actionProcessor = Mockito.mock(DefaultActionProcessor.class);
    final var vectorDBRequest = createDefaultRetrieve();
    Mockito.when(ctx.bindVariables(EmbeddingsVectorDBRequest.class)).thenReturn(vectorDBRequest);
    final var connectorEntryPoint = new EmbeddingsVectorDBFunction(actionProcessor);

    connectorEntryPoint.execute(ctx);

    Mockito.verify(actionProcessor).handleFlow(vectorDBRequest, ctx);
  }
}
