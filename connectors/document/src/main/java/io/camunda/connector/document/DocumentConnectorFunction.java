/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.document;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.document.store.DocumentCreationRequest;
import java.util.Map;

@ElementTemplate(
    id = "connector-document",
    name = "Document Connector",
    description = "Document Connector",
    inputDataClass = DocumentConnectorProperties.class)
@OutboundConnector(
    name = "document",
    type = "document",
    inputVariables = {"document", "content", "operation"})
public class DocumentConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    final var properties = context.bindVariables(DocumentConnectorProperties.class);
    if (properties.operation() instanceof DocumentOperation.GetDocument getDocument) {
      return new String(getDocument.document().asByteArray());
    } else if (properties.operation() instanceof DocumentOperation.CreateDocument createDocument) {
      return context.createDocument(
          DocumentCreationRequest.from(createDocument.content().getBytes())
              .metadata(Map.of())
              .build());
    } else {
      throw new IllegalArgumentException("Unsupported operation");
    }
  }
}
