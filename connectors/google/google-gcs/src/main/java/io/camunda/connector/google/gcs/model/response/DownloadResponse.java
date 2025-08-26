/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.response;

import io.camunda.connector.api.document.Document;

public sealed interface DownloadResponse
    permits DownloadResponse.DocumentContent, DownloadResponse.StringContent {
  record DocumentContent(Document document) implements DownloadResponse {}

  record StringContent(String content) implements DownloadResponse {}
}
