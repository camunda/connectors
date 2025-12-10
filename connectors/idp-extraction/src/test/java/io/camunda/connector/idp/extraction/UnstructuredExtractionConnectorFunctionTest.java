/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.service.UnstructuredService;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnstructuredExtractionConnectorFunctionTest {

  @Mock private UnstructuredService unstructuredService;

  private UnstructuredExtractionConnectorFunction connectorFunction;

  @BeforeEach
  void setUp() {
    connectorFunction = new UnstructuredExtractionConnectorFunction();
    // Use reflection to inject the mock service
    try {
      var field =
          UnstructuredExtractionConnectorFunction.class.getDeclaredField("unstructuredService");
      field.setAccessible(true);
      field.set(connectorFunction, unstructuredService);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock", e);
    }
  }

  @Test
  void execute_ShouldCallUnstructuredService_WithCorrectParameters() throws Exception {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "extractor": {
                    "type": "textractExtractor",
                    "awsAuthType": "credentials",
                    "accessKey": "test-key",
                    "secretKey": "test-secret",
                    "region": "us-east-1",
                    "s3BucketName": "test-bucket"
                  },
                  "ai": {
                    "type": "openAi",
                    "openAiEndpoint": "https://api.openai.com/v1",
                    "openAiHeaders": {
                      "Authorization": "Bearer test-key"
                    }
                  },
                  "input": {
                    "document": {
                      "camunda.document.type": "camunda",
                      "storeId": "test",
                      "documentId": "test",
                      "metadata": {}
                    },
                    "converseData": {
                      "modelId": "test-model"
                    },
                    "taxonomyItems": [
                      {
                        "name": "invoiceNumber",
                        "prompt": "Extract the invoice number"
                      },
                      {
                        "name": "totalAmount",
                        "prompt": "Extract the total amount"
                      }
                    ]
                  }
                }
                """)
            .build();

    var expectedResult =
        new ExtractionResult(Map.of("invoiceNumber", "INV-12345", "totalAmount", "$1000.00"), null);

    when(unstructuredService.extract(
            any(TextExtractor.class), any(AiClient.class), anyList(), any(Document.class)))
        .thenReturn(expectedResult);

    // when
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    verify(unstructuredService)
        .extract(any(TextExtractor.class), any(AiClient.class), anyList(), any(Document.class));
  }

  @Test
  void execute_ShouldHandleMultipleTaxonomyItems() throws Exception {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "extractor": {
                    "type": "textractExtractor",
                    "awsAuthType": "credentials",
                    "accessKey": "test-key",
                    "secretKey": "test-secret",
                    "region": "us-east-1",
                    "s3BucketName": "test-bucket"
                  },
                  "ai": {
                    "type": "openAi",
                    "openAiEndpoint": "https://api.openai.com/v1",
                    "openAiHeaders": {
                      "Authorization": "Bearer test-key"
                    }
                  },
                  "input": {
                    "document": {
                      "camunda.document.type": "camunda",
                      "storeId": "test",
                      "documentId": "test",
                      "metadata": {}
                    },
                    "converseData": {
                      "modelId": "test-model"
                    },
                    "taxonomyItems": [
                      {
                        "name": "supplier",
                        "prompt": "Who is the supplier"
                      },
                      {
                        "name": "sum",
                        "prompt": "What is the total sum"
                      },
                      {
                        "name": "date",
                        "prompt": "What is the invoice date"
                      }
                    ]
                  }
                }
                """)
            .build();

    var expectedResult =
        new ExtractionResult(
            Map.of("supplier", "Acme Corp", "sum", "$500.00", "date", "2023-12-01"), null);

    when(unstructuredService.extract(
            any(TextExtractor.class), any(AiClient.class), anyList(), any(Document.class)))
        .thenReturn(expectedResult);

    // when
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    assertThat(((ExtractionResult) result).extractedFields()).hasSize(3);
    verify(unstructuredService)
        .extract(any(TextExtractor.class), any(AiClient.class), anyList(), any(Document.class));
  }
}
