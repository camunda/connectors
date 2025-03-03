/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.util;

import static org.apache.hc.core5.http.ContentType.APPLICATION_PDF;

import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.model.TextExtractionEngineType;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

public class ExtractionTestUtils {

  public static final String ACTUAL_ACCESS_KEY = "DDDCCCBBBBAAAA";
  public static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  public static final String TEXTRACT_EXTRACTION_INPUT_JSON =
      """
      {
        "input": {
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
        },
        "baseRequest": {
          "configuration": {
            "region": "us-east-1",
            "s3BucketName": "test-aws-s3-bucket-name",
          },
          "authentication": {
            "type": "defaultCredentialsChain",
            "accessKey": "{{secrets.ACCESS_KEY}}",
            "secretKey": "{{secrets.SECRET_KEY}}"
          }
        }
      }
      """;

  public static final ExtractionRequestData TEXTRACT_EXTRACTION_REQUEST_DATA =
      new ExtractionRequestData(
          TextExtractionEngineType.AWS_TEXTRACT,
          loadTestFile(),
          List.of(
              new TaxonomyItem("sum", "the total amount that was paid for this invoice"),
              new TaxonomyItem("supplier", "who provided the goods or services")),
          new ConverseData("anthropic.claude-3-5-sonnet-20240620-v1:0", 512, 0.5f, 0.9f));

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
