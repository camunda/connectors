/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.caller.VertexCaller;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.supplier.BedrockRuntimeClientSupplier;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnstructuredService implements ExtractionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnstructuredService.class);

  private final TextractClientSupplier textractClientSupplier;

  private final S3ClientSupplier s3ClientSupplier;

  private final BedrockRuntimeClientSupplier bedrockRuntimeClientSupplier;

  private final PollingTextractCaller pollingTextractCaller;

  private final BedrockCaller bedrockCaller;

  private final ObjectMapper objectMapper;

  private final VertexCaller vertexCaller;

  public UnstructuredService() {
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
    this.bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    this.pollingTextractCaller = new PollingTextractCaller();
    this.bedrockCaller = new BedrockCaller();
    this.vertexCaller = new VertexCaller();
    this.objectMapper = new ObjectMapper();
  }

  public UnstructuredService(
      TextractClientSupplier textractClientSupplier,
      S3ClientSupplier s3ClientSupplier,
      BedrockRuntimeClientSupplier bedrockRuntimeClientSupplier,
      PollingTextractCaller pollingTextractCaller,
      BedrockCaller bedrockCaller,
      VertexCaller vertexCaller,
      ObjectMapper objectMapper) {
    this.textractClientSupplier = textractClientSupplier;
    this.s3ClientSupplier = s3ClientSupplier;
    this.bedrockRuntimeClientSupplier = bedrockRuntimeClientSupplier;
    this.pollingTextractCaller = pollingTextractCaller;
    this.bedrockCaller = bedrockCaller;
    this.vertexCaller = vertexCaller;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object extract(ExtractionRequest extractionRequest) {
    final var input = extractionRequest.input();
    return switch (extractionRequest.baseRequest()) {
      case AwsProvider aws -> extractUsingAws(input, aws);
      case GcpProvider gemini -> extractUsingGcp(input, gemini);
      default ->
          throw new IllegalStateException("Unexpected value: " + extractionRequest.baseRequest());
    };
  }

  private ExtractionResult extractUsingGcp(ExtractionRequestData input, GcpProvider baseRequest) {
    try {
      long startTime = System.currentTimeMillis();
      Object result = vertexCaller.generateContent(input, baseRequest);
      long endTime = System.currentTimeMillis();
      LOGGER.info("Gemini content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(
          buildResponseJsonIfPossible(result.toString(), input.taxonomyItems()));
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
              baseRequest.getConfiguration().region(),
              extractedText,
              bedrockRuntimeClientSupplier.getBedrockRuntimeClient(baseRequest));
      long endTime = System.currentTimeMillis();
      LOGGER.info("Aws content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(
          buildResponseJsonIfPossible(bedrockResponse, input.taxonomyItems()));
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private Map<String, Object> buildResponseJsonIfPossible(
      String llmResponse, List<TaxonomyItem> taxonomyItems) {
    try {
      var llmResponseJson = objectMapper.readValue(llmResponse, JsonNode.class);
      var taxonomyItemsNames = taxonomyItems.stream().map(TaxonomyItem::name).toList();

      if (llmResponseJson.isObject()) {
        if (llmResponseJson.has("response")
            && llmResponseJson.size() == 1
            && !taxonomyItemsNames.contains("response")) {
          var nestedResponse = llmResponseJson.get("response");
          if (nestedResponse.isObject()) {
            llmResponseJson = nestedResponse;
          } else if (nestedResponse.isTextual()) {
            llmResponseJson = objectMapper.readValue(nestedResponse.asText(), JsonNode.class);
          } else {
            throw new ConnectorException(
                String.valueOf(HttpStatus.SC_SERVER_ERROR),
                String.format(
                    "LLM response is neither a JSON object nor a string: %s", llmResponse));
          }
        }

        Map<String, Object> result =
            taxonomyItemsNames.stream()
                .filter(llmResponseJson::has)
                .collect(Collectors.toMap(name -> name, llmResponseJson::get));

        var missingKeys =
            taxonomyItemsNames.stream().filter(name -> !result.containsKey(name)).toList();
        if (!missingKeys.isEmpty()) {
          LOGGER.warn(
              "LLM model response is missing the following keys: ({})",
              String.join(", ", missingKeys));
        }

        return result;

      } else {
        throw new ConnectorException(
            String.valueOf(HttpStatus.SC_SERVER_ERROR),
            String.format("LLM response is not a JSON object: %s", llmResponse));
      }
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_SERVER_ERROR),
          String.format("Failed to parse the JSON response from LLM: %s", llmResponse),
          e);
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
