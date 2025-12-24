/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.DocumentLine;
import com.azure.ai.documentintelligence.models.DocumentPage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.polling.SyncPoller;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureDocumentIntelligenceExtractionClient implements TextExtractor, AutoCloseable {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AzureDocumentIntelligenceExtractionClient.class);
  private final DocumentIntelligenceClient documentIntelligenceClient;

  public AzureDocumentIntelligenceExtractionClient(String endpoint, String apiKey) {
    documentIntelligenceClient =
        new DocumentIntelligenceClientBuilder()
            .endpoint(endpoint)
            .credential(new AzureKeyCredential(apiKey))
            .buildClient();
  }

  @Override
  public void close() {
    // Azure DocumentIntelligenceClient doesn't require explicit cleanup as it's a synchronous
    // client
    // This method is implemented for consistency with other extraction clients
    LOGGER.debug("AzureDocumentIntelligenceExtractionClient closed");
  }

  @Override
  public String extract(Document document) {
    try {
      SyncPoller<AnalyzeOperationDetails, AnalyzeResult> analyzePoller;

      try {
        DocumentLinkParameters linkParams = new DocumentLinkParameters(Duration.ofMinutes(2));
        String documentLink = document.generateLink(linkParams);
        // Use the document link for analysis
        analyzePoller =
            documentIntelligenceClient.beginAnalyzeDocument(
                "prebuilt-read", new AnalyzeDocumentOptions(documentLink));
      } catch (Exception e) {
        LOGGER.error(
            "Document link generation for AnalyzeDocumentOptions input not supported, falling back to byte array input");
        // Fall back to using byte array
        analyzePoller =
            documentIntelligenceClient.beginAnalyzeDocument(
                "prebuilt-read", new AnalyzeDocumentOptions(document.asByteArray()));
      }

      // Wait for analysis to complete and get results
      AnalyzeResult result = analyzePoller.getFinalResult();

      // Extract text from all pages
      StringBuilder extractedText = new StringBuilder();

      if (result.getPages() != null) {
        for (DocumentPage page : result.getPages()) {
          if (page.getLines() != null) {
            for (DocumentLine line : page.getLines()) {
              extractedText.append(line.getContent());
              extractedText.append("\n");
            }
          }
        }
      }

      return extractedText.toString().trim();

    } catch (Exception e) {
      throw new RuntimeException("Failed to extract text from document: " + e.getMessage(), e);
    }
  }
}
