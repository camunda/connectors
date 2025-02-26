/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.supplier.BedrockRuntimeClientSupplier;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "input", label = "Input message data")},
    inputDataClass = ExtractionRequest.class)
public class ExtractionConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionConnectorFunction.class);

  private final TextractClientSupplier textractClientSupplier;

  private final S3ClientSupplier s3ClientSupplier;

  private final BedrockRuntimeClientSupplier bedrockRuntimeClientSupplier;

  private final PollingTextractCaller pollingTextractCaller;

  private final BedrockCaller bedrockCaller;

  private final ObjectMapper objectMapper;

  public ExtractionConnectorFunction() {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.pollingTextractCaller = new PollingTextractCaller();
    this.bedrockCaller = new BedrockCaller();
    this.objectMapper = new ObjectMapper();
  }

  public ExtractionConnectorFunction(
      PollingTextractCaller pollingTextractCaller, BedrockCaller bedrockCaller) {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.objectMapper = new ObjectMapper();
    this.pollingTextractCaller = pollingTextractCaller;
    this.bedrockCaller = bedrockCaller;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var extractionRequest = context.bindVariables(ExtractionRequest.class);

    try {
      String extractedText =
          switch (extractionRequest.input().extractionEngineType()) {
            case AWS_TEXTRACT -> extractTextUsingAwsTextract(extractionRequest);
            case APACHE_PDFBOX -> extractTextUsingApachePdf(extractionRequest);
          };

      String bedrockResponse =
          bedrockCaller.call(
              extractionRequest,
              extractedText,
              bedrockRuntimeClientSupplier.getBedrockRuntimeClient(extractionRequest));

      return new ExtractionResult(
          buildResponseJsonIfPossible(bedrockResponse, extractionRequest.input().taxonomyItems()));
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private Map<String, JsonNode> buildResponseJsonIfPossible(
      String llmResponse, List<TaxonomyItem> taxonomyItems) {
    try {
      var llmResponseJson = objectMapper.readValue(llmResponse, JsonNode.class);
      var taxonomyItemsNames = taxonomyItems.stream().map(TaxonomyItem::name).toList();

      if (llmResponseJson.has("response")
          && llmResponseJson.size() == 1
          && !taxonomyItemsNames.contains("response")) {
        var nestedResponse = llmResponseJson.get("response");
        if (nestedResponse.isObject()) {
          llmResponseJson = nestedResponse;
        } else if (nestedResponse.isTextual()) {
          llmResponseJson = objectMapper.readValue(nestedResponse.asText(), JsonNode.class);
        } else {
          llmResponseJson = objectMapper.createObjectNode();
        }
      }

      var result = taxonomyItemsNames.stream()
          .filter(llmResponseJson::has)
          .collect(Collectors.toMap(name -> name, llmResponseJson::get));

      var missingKeys = taxonomyItemsNames.stream().filter(name -> !result.containsKey(name)).toList();
      if (!missingKeys.isEmpty()) {
        LOGGER.warn("LLM model response is missing the following keys: ({})", String.join(", ", missingKeys));
      }

      return result;
    } catch (JsonProcessingException e) {
      LOGGER.error(
          String.format("Failed to parse the JSON response from LLM: %s", llmResponse),
          e.getMessage());
      return Map.of();
    }
  }

  private String extractTextUsingAwsTextract(ExtractionRequest extractionRequest) throws Exception {
    return pollingTextractCaller.call(
        extractionRequest.input().document(),
        extractionRequest.input().s3BucketName(),
        textractClientSupplier.getTextractClient(extractionRequest),
        s3ClientSupplier.getAsyncS3Client(extractionRequest));
  }

  private String extractTextUsingApachePdf(ExtractionRequest extractionRequest) throws Exception {
    PDDocument document = Loader.loadPDF(extractionRequest.input().document().asByteArray());
    PDFTextStripper pdfStripper = new PDFTextStripper();
    return pdfStripper.getText(document);
  }
}
