/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import java.util.List;

/**
 * Converts a list of {@link AdHocToolElement} to a {@link AdHocToolsSchemaResponse} containing tool
 * definitions and gateway tool definitions.
 */
public interface AdHocToolsSchemaResolver {

  /**
   * The extension property of a tool activity that sets the document delivery of the documents the
   * tool returns. The value {@value #DOCUMENT_DELIVERY_CONTENT} sets the delivery {@code content},
   * so the agent adds the content of the documents to the request; any other value, or no property,
   * keeps the delivery {@code reference}.
   */
  String DOCUMENT_DELIVERY_EXTENSION = "io.camunda.agenticai.documents.delivery";

  /** The value of {@link #DOCUMENT_DELIVERY_EXTENSION} that sets the delivery {@code content}. */
  String DOCUMENT_DELIVERY_CONTENT = "content";

  AdHocToolsSchemaResponse resolveAdHocToolsSchema(List<AdHocToolElement> elements);
}
