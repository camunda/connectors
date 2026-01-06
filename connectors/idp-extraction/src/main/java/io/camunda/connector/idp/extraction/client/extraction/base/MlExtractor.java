/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction.base;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;

public interface MlExtractor {
  StructuredExtractionResponse runDocumentAnalysis(Document document);
}
