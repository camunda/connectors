/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;

@TemplateSubType(
    id = "download",
    label = "Download file",
    description = "Download a file from Google Drive",
    keywords = {
      "download",
      "download file",
      "google drive",
      "retrieve file",
      "get file",
      "export file"
    })
public record DownloadResource(@Valid DownloadData downloadData) implements Resource {}
