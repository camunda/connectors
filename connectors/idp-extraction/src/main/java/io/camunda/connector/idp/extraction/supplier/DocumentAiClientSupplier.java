/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.supplier;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;

public class DocumentAiClientSupplier {

  public DocumentProcessorServiceClient getDocumentAiClient(GcpAuthentication authentication)
      throws IOException {
    DocumentProcessorServiceSettings settings =
        DocumentProcessorServiceSettings.newBuilder()
            .setCredentialsProvider(() -> GcsUtil.getCredentials(authentication))
            .build();

    return DocumentProcessorServiceClient.create(settings);
  }
}
