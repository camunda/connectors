/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.DocumentLine;
import com.azure.ai.documentintelligence.models.DocumentPage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.polling.SyncPoller;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureDocumentIntelligenceCaller {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AzureDocumentIntelligenceCaller.class);

  public String call(ExtractionRequestData input, AzureProvider baseRequest) {
    LOGGER.debug("Calling Azure Document Intelligence with extraction request data: {}", input);

    DocumentIntelligenceClient documentIntelligenceClient =
        new DocumentIntelligenceClientBuilder()
            .endpoint(baseRequest.getDocumentIntelligenceConfiguration().getEndpoint())
            .credential(
                new AzureKeyCredential(
                    baseRequest.getDocumentIntelligenceConfiguration().getApiKey()))
            .buildClient();

    try {
      SyncPoller<AnalyzeOperationDetails, AnalyzeResult> analyzePoller;

      try {
        DocumentLinkParameters linkParams = new DocumentLinkParameters(Duration.ofMinutes(2));
        String documentLink = input.document().generateLink(linkParams);
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
                "prebuilt-read", new AnalyzeDocumentOptions(input.document().asByteArray()));
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
