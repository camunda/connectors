/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.GeminiCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.GeminiProvider;
import io.camunda.connector.idp.extraction.supplier.BedrockRuntimeClientSupplier;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "IDP extraction outbound Connector",
    inputVariables = {"baseRequest", "input"},
    type = "io.camunda:idp-extraction-connector-template:1")
@ElementTemplate(
    id = "io.camunda.connector.IdpExtractionOutBoundTemplate.v1",
    name = "IDP extraction outbound Connector",
    version = 1,
    description = "Execute IDP extraction requests",
    icon = "icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      //      @ElementTemplate.PropertyGroup(id = "provider", label = "Provider selection"),
      //      @ElementTemplate.PropertyGroup(id = "authentication", label = "Provider
      // authentication"),
      //      @ElementTemplate.PropertyGroup(id = "configuration", label = "Provider
      // configuration"),
    },
    inputDataClass = ExtractionRequest.class)
public class ExtractionConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionConnectorFunction.class);

  private final TextractClientSupplier textractClientSupplier;

  private final S3ClientSupplier s3ClientSupplier;

  private final BedrockRuntimeClientSupplier bedrockRuntimeClientSupplier;

  private final PollingTextractCaller pollingTextractCaller;

  private final BedrockCaller bedrockCaller;

  private final GeminiCaller geminiCaller;

  public ExtractionConnectorFunction() {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.pollingTextractCaller = new PollingTextractCaller();
    this.bedrockCaller = new BedrockCaller();
    this.geminiCaller = new GeminiCaller();
  }

  public ExtractionConnectorFunction(
      PollingTextractCaller pollingTextractCaller,
      BedrockCaller bedrockCaller,
      GeminiCaller geminiCaller) {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.pollingTextractCaller = pollingTextractCaller;
    this.bedrockCaller = bedrockCaller;
    this.geminiCaller = geminiCaller;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var extractionRequest = context.bindVariables(ExtractionRequest.class);
    final var input = extractionRequest.input();
    return switch (extractionRequest.baseRequest()) {
      case AwsProvider aws -> extractUsingAws(input, aws);
      case GeminiProvider gemini -> extractUsingGcp(input, gemini);
    };
  }

  private ExtractionResult extractUsingGcp(
      ExtractionRequestData input, GeminiProvider baseRequest) {
    try {
      long startTime = System.currentTimeMillis();
      Object result = geminiCaller.generateContent(input, baseRequest);
      long endTime = System.currentTimeMillis();
      LOGGER.info("Gemini content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(result);
    } catch (Exception e) {
      LOGGER.error("Document extraction via GCP failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private ExtractionResult extractUsingAws(ExtractionRequestData input, AwsProvider baseRequest) {
    try {
      long startTime = System.currentTimeMillis();
      String extractedText =
          switch (baseRequest.getExtractionEngineType()) {
            case AWS_TEXTRACT -> extractTextUsingAwsTextract(input, baseRequest);
            case APACHE_PDFBOX -> extractTextUsingApachePdf(input);
          };

      String bedrockResponse =
          bedrockCaller.call(
              input,
              extractedText,
              bedrockRuntimeClientSupplier.getBedrockRuntimeClient(baseRequest));
      long endTime = System.currentTimeMillis();
      LOGGER.info("Aws content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(bedrockResponse);
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private String extractTextUsingAwsTextract(ExtractionRequestData input, AwsProvider baseRequest)
      throws Exception {
    return pollingTextractCaller.call(
        input.document(),
        baseRequest.getS3BucketName(),
        textractClientSupplier.getTextractClient(baseRequest),
        s3ClientSupplier.getAsyncS3Client(baseRequest));
  }

  private String extractTextUsingApachePdf(ExtractionRequestData input) throws Exception {
    PDDocument document = Loader.loadPDF(input.document().asByteArray());
    PDFTextStripper pdfStripper = new PDFTextStripper();
    return pdfStripper.getText(document);
  }
}
