/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.util;

import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.model.TextExtractionEngineType;
import java.util.List;

public class ExtractionTestUtils {

  public static final String ACTUAL_ACCESS_KEY = "DDDCCCBBBBAAAA";
  public static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  public static final String TEXTRACT_EXTRACTION_INPUT_JSON =
      """
                    {
                      "input": {
                        "extractionEngineType": "AWS_TEXTRACT",
                        "documentUrl": "https://some-url-containing-your-document/documemt.pdf",
                        "s3BucketName": "test-aws-s3-bucket-name",
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
                      "configuration": {
                        "region": "us-east-1"
                      },
                      "authentication": {
                        "type": "defaultCredentialsChain",
                        "accessKey": "{{secrets.ACCESS_KEY}}",
                        "secretKey": "{{secrets.SECRET_KEY}}"
                      }
                    }
                    """;

  public static final ExtractionRequestData TEXTRACT_EXTRACTION_REQUEST_DATA =
      new ExtractionRequestData(
          TextExtractionEngineType.AWS_TEXTRACT,
          "https://some-url-containing-your-document/documemt.pdf",
          "test-aws-s3-bucket-name",
          List.of(
              new TaxonomyItem("sum", "the total amount that was paid for this invoice"),
              new TaxonomyItem("supplier", "who provided the goods or services")),
          new ConverseData("anthropic.claude-3-5-sonnet-20240620-v1:0", 512, 0.5f, 0.9f));
}
