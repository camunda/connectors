/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.util;

import static org.apache.hc.core5.http.ContentType.APPLICATION_PDF;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.ExtractionType;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ExtractionTestUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final String ACTUAL_ACCESS_KEY = "DDDCCCBBBBAAAA";
  public static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  public static final String AWS_BASE_REQUEST =
      """
        {
          "configuration": {
            "region": "us-east-1"
          },
          "authentication": {
            "type": "credentials",
            "accessKey": "{{secrets.ACCESS_KEY}}",
            "secretKey": "{{secrets.SECRET_KEY}}"
          },
          "s3BucketName": "test-aws-s3-bucket-name",
          "extractionEngineType": "AWS_TEXTRACT"
        }
        """;
  public static final String AWS_INPUT =
      """
        {
          "extractionEngineType": "AWS_TEXTRACT",
          "document": {
            "camunda.document.type": "camunda",
            "storeId": "test",
            "documentId": "test",
            "metadata": {}
          },
          "converseData": {
            "modelId": "anthropic.claude-3-5-sonnet-20240620-v1:0"
          },
          "taxonomyItems": [
            {
              "name": "sum",
              "prompt": "the total amount that was paid for this invoice"
            },
            {
              "name": "supplier",
              "prompt": "who provided the goods or services"
            }
          ]
        }
        """;
  public static final String TEXTRACT_EXTRACTION_INPUT_JSON =
      """
      {
        "input": %s,
        "baseRequest": %s
      }
      """
          .formatted(AWS_INPUT, AWS_BASE_REQUEST);

  public static final ExtractionRequestData TEXTRACT_EXTRACTION_REQUEST_DATA =
      new ExtractionRequestData(
          loadTestFile(),
          ExtractionType.UNSTRUCTURED,
          List.of(
              new TaxonomyItem("sum", "the total amount that was paid for this invoice"),
              new TaxonomyItem("supplier", "who provided the goods or services")),
          List.of(),
          Map.of(),
          null,
          new ConverseData("anthropic.claude-3-5-sonnet-20240620-v1:0", 512, 0.5f, 0.9f));

  /**
   * Creates an AwsProvider instance from the default AWS_BASE_REQUEST JSON.
   *
   * @return configured AwsProvider instance
   */
  public static AwsProvider createDefaultAwsProvider() {
    try {
      return OBJECT_MAPPER.readValue(AWS_BASE_REQUEST, AwsProvider.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse AWS provider JSON", e);
    }
  }

  private static Document loadTestFile() {
    DocumentFactory documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    try {
      FileInputStream fileInputStream =
          new FileInputStream("src/test/resources/sample-invoice.pdf");
      return documentFactory.create(
          DocumentCreationRequest.from(fileInputStream)
              .contentType(APPLICATION_PDF.getMimeType())
              .fileName("sample-invoice")
              .build());
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
