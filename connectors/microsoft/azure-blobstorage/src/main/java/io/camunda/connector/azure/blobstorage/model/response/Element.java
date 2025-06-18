/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.response;

import io.camunda.document.Document;

public sealed interface Element permits Element.DocumentContent, Element.StringContent {
  record DocumentContent(Document document) implements Element {}

  record StringContent(String content) implements Element {}
}
