/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF text extractor using in-machine Apache PDFBox. This extractor provides basic text extraction
 * without ML-based analysis.
 */
public class PdfBoxExtractionClient implements TextExtractor {

  private static final Logger LOGGER = LoggerFactory.getLogger(PdfBoxExtractionClient.class);

  @Override
  public String extract(Document document) {
    try (InputStream inputStream = document.asInputStream();
        PDDocument pdfDoc = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {

      PDFTextStripper pdfStripper = new PDFTextStripper();
      String extractedText = pdfStripper.getText(pdfDoc);

      LOGGER.debug("PDFBox extracted {} characters of text from document", extractedText.length());

      return extractedText;
    } catch (IOException e) {
      LOGGER.error("Error while extracting text from PDF document", e);
      throw new RuntimeException("Failed to extract text from PDF document: " + e.getMessage(), e);
    }
  }
}
