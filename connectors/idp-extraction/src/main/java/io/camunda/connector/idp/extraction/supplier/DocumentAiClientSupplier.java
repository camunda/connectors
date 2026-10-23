/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.supplier;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;
import java.util.Locale;

public class DocumentAiClientSupplier {

  public DocumentProcessorServiceClient getDocumentAiClient(
      GcpAuthentication authentication, String region) throws IOException {
    return DocumentProcessorServiceClient.create(buildSettings(authentication, region));
  }

  public static DocumentProcessorServiceSettings buildSettings(
      GcpAuthentication authentication, String region) throws IOException {
    // The default endpoint only serves processors in the 'us' location
    return DocumentProcessorServiceSettings.newBuilder()
        .setCredentialsProvider(() -> GcsUtil.getCredentials(authentication))
        .setEndpoint(String.format("%s-documentai.googleapis.com:443", normalizeRegion(region)))
        .build();
  }

  public static String normalizeRegion(String region) {
    if (region == null || region.isBlank()) {
      throw new ConnectorInputException(
          "Document AI region must not be empty. Use the location of the processor, e.g. 'eu' or 'us'.");
    }
    return region.trim().toLowerCase(Locale.ROOT);
  }
}
