/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.mapper;

import com.google.api.services.drive.model.File;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;

public class DocumentMapper {

  private final OutboundConnectorContext context;

  public DocumentMapper(OutboundConnectorContext context) {
    this.context = context;
  }

  public Document mapToDocument(byte[] bytes, File fileMetaData) {
    return context.createDocument(
        DocumentCreationRequest.from(bytes)
            .contentType(fileMetaData.getMimeType())
            .fileName(fileMetaData.getName())
            .build());
  }
}
