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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.service.StructuredService;
import io.camunda.connector.idp.extraction.service.UnstructuredService;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionConnectorFunctionTest {

  @Mock private UnstructuredService unstructuredService;
  @Mock private StructuredService structuredService;

  @InjectMocks private ExtractionConnectorFunction extractionConnectorFunction;

  @Test
  void execute_ShouldCallUnstructuredService_WhenExtractionTypeIsUnstructured() {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "baseRequest": {
                    "type": "aws",
                    "authentication": {
                      "type": "credentials",
                      "accessKey": "test-key",
                      "secretKey": "test-secret"
                    },
                    "configuration": {
                      "region": "us-east-1"
                    },
                    "s3BucketName": "test-bucket",
                    "extractionEngineType": "AWS_TEXTRACT",
                    "converseData": {
                      "modelId": "test-model"
                    }
                  },
                  "input": {
                    "extractionType": "UNSTRUCTURED",
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
                        "name": "sum",
                        "prompt": "total amount"
                      }
                    ]
                  }
                }
                """)
            .build();

    var expectedResult = new ExtractionResult(Map.of("sum", "$12.25"), null);
    when(unstructuredService.extract(
            any(TextExtractor.class), any(AiClient.class), anyList(), any(Document.class)))
        .thenReturn(expectedResult);

    // when
    var result = extractionConnectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    verify(unstructuredService)
        .extract(any(TextExtractor.class), any(AiClient.class), anyList(), any(Document.class));
  }

  @Test
  void execute_ShouldCallStructuredService_WhenExtractionTypeIsStructured() {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "baseRequest": {
                    "type": "aws",
                    "authentication": {
                      "type": "credentials",
                      "accessKey": "test-key",
                      "secretKey": "test-secret"
                    },
                    "configuration": {
                      "region": "us-east-1"
                    },
                    "s3BucketName": "test-bucket",
                    "extractionEngineType": "AWS_TEXTRACT"
                  },
                  "input": {
                    "extractionType": "STRUCTURED",
                    "document": {
                      "camunda.document.type": "camunda",
                      "storeId": "test",
                      "documentId": "test",
                      "metadata": {}
                    },
                    "includedFields": ["field1", "field2"],
                    "renameMappings": {},
                    "delimiter": ","
                  }
                }
                """)
            .build();

    var expectedResult =
        new StructuredExtractionResult(Map.of("field1", "value1"), Map.of(), Map.of(), Map.of());
    when(structuredService.extract(
            any(MlExtractor.class), anyList(), anyMap(), anyString(), any(Document.class)))
        .thenReturn(expectedResult);

    // when
    var result = extractionConnectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    verify(structuredService)
        .extract(any(MlExtractor.class), anyList(), anyMap(), anyString(), any(Document.class));
  }
}
