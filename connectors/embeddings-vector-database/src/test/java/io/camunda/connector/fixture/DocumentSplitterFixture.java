/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.embedding.splitter.DocumentSplitterRecursive;
import io.camunda.connector.model.embedding.splitter.NoopDocumentSplitter;

public class DocumentSplitterFixture {

  public static NoopDocumentSplitter noopDocumentSplitter() {
    return new NoopDocumentSplitter();
  }

  public static DocumentSplitterRecursive documentSplitterRecursive() {
    return new DocumentSplitterRecursive(500, 80);
  }
}
