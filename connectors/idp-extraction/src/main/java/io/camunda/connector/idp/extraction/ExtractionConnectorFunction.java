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
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.GeminiCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.*;
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
    inputVariables = {"baseRequest", "providerConfiguration", "input"},
    type = "io.camunda:idp-extraction-connector-template:1")
@ElementTemplate(
    id = "io.camunda.connector.IdpExtractionOutBoundTemplate.v1",
    name = "IDP extraction outbound Connector",
    version = 1,
    description = "Execute IDP extraction requests",
    icon = "icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "input", label = "Input message data")},
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

    return switch (extractionRequest.input().extractionEngineType()) {
      case GCP_GEMINI -> extractUsingGcp(extractionRequest);
      case APACHE_PDFBOX, AWS_TEXTRACT -> extractUsingAws(extractionRequest);
    };
  }

  private ExtractionResult extractUsingGcp(ExtractionRequest extractionRequest) {
    try {
      long startTime = System.currentTimeMillis();
      Object result = geminiCaller.generateContent(extractionRequest);
      long endTime = System.currentTimeMillis();
      LOGGER.info("Gemini content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(result);
    } catch (Exception e) {
      LOGGER.error(
          "Document extraction via {} failed: {}",
          extractionRequest.input().extractionEngineType(),
          e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private ExtractionResult extractUsingAws(ExtractionRequest extractionRequest) {
    AwsBaseRequest baseRequest = getAwsBaseRequest(extractionRequest);
    try {
      long startTime = System.currentTimeMillis();
      String extractedText =
          switch (extractionRequest.input().extractionEngineType()) {
            case AWS_TEXTRACT ->
                extractTextUsingAwsTextract(extractionRequest.input(), baseRequest);
            case APACHE_PDFBOX -> extractTextUsingApachePdf(extractionRequest);
            default ->
                throw new ConnectorException("Unsupported extraction engine for AWS provider");
          };

      String bedrockResponse =
          bedrockCaller.call(
              extractionRequest,
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

  private String extractTextUsingAwsTextract(
      ExtractionRequestData input, AwsBaseRequest baseRequest) throws Exception {
    return pollingTextractCaller.call(
        input.document(),
        input.s3BucketName(),
        textractClientSupplier.getTextractClient(baseRequest),
        s3ClientSupplier.getAsyncS3Client(baseRequest));
  }

  private String extractTextUsingApachePdf(ExtractionRequest extractionRequest) throws Exception {
    PDDocument document = Loader.loadPDF(extractionRequest.input().document().asByteArray());
    PDFTextStripper pdfStripper = new PDFTextStripper();
    return pdfStripper.getText(document);
  }

  // for compatibility with older versions of web-modeler
  private AwsBaseRequest getAwsBaseRequest(ExtractionRequest extractionRequest) {
    if (extractionRequest.baseRequest() != null) {
      return extractionRequest.baseRequest();
    } else if (extractionRequest.providerConfiguration() != null
        && extractionRequest.providerConfiguration().awsRequest() != null) {
      return extractionRequest.providerConfiguration().awsRequest();
    } else {
      throw new ConnectorException("Aws request is not provided");
    }
  }
}
