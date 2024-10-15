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
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.supplier.BedrockRuntimeClientSupplier;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import java.net.URI;
import java.net.URL;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "IDP extraction outbound Connector",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:idp-extraction-connector-template:1")
@ElementTemplate(
    id = "io.camunda.connector.IdpExtractionOutBoundTemplate.v1",
    name = "IDP extraction outbound Connector",
    version = 1,
    description = "Execute IDP extraction requests",
    icon = "icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data")
    },
    inputDataClass = ExtractionRequest.class)
public class ExtractionConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionConnectorFunction.class);

  private final TextractClientSupplier textractClientSupplier;

  private final S3ClientSupplier s3ClientSupplier;

  private final BedrockRuntimeClientSupplier bedrockRuntimeClientSupplier;

  private final PollingTextractCaller pollingTextractCaller;

  private final BedrockCaller bedrockCaller;

  public ExtractionConnectorFunction() {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.pollingTextractCaller = new PollingTextractCaller();
    this.bedrockCaller = new BedrockCaller();
  }

  public ExtractionConnectorFunction(
      PollingTextractCaller pollingTextractCaller, BedrockCaller bedrockCaller) {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.pollingTextractCaller = pollingTextractCaller;
    this.bedrockCaller = bedrockCaller;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var extractionRequest = context.bindVariables(ExtractionRequest.class);

    try {
      String extractedText =
          switch (extractionRequest.getInput().extractionEngineType()) {
            case AWS_TEXTRACT -> extractTextUsingAwsTextract(extractionRequest);
            case APACHE_PDFBOX -> extractTextUsingApachePdf(extractionRequest);
          };

      String bedrockResponse =
          bedrockCaller.call(
              extractionRequest,
              extractedText,
              bedrockRuntimeClientSupplier.getBedrockRuntimeClient(extractionRequest));

      return new ExtractionResult(bedrockResponse);
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private String extractTextUsingAwsTextract(ExtractionRequest extractionRequest) throws Exception {
    return pollingTextractCaller.call(
        extractionRequest.getInput().documentUrl(),
        extractionRequest.getInput().s3BucketName(),
        textractClientSupplier.getTextractClient(extractionRequest),
        s3ClientSupplier.getAsyncS3Client(extractionRequest));
  }

  private String extractTextUsingApachePdf(ExtractionRequest extractionRequest) throws Exception {
    String documentUrl = extractionRequest.getInput().documentUrl();
    URL url = URI.create(documentUrl).toURL();
    PDDocument document = Loader.loadPDF(IOUtils.toByteArray(url.openStream()));
    PDFTextStripper pdfStripper = new PDFTextStripper();
    return pdfStripper.getText(document);
  }
}
