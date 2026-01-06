/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.idp.extraction.model.ClassificationResult;
import io.camunda.connector.idp.extraction.request.classification.ClassificationRequest;
import io.camunda.connector.idp.extraction.service.ClassificationService;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassificationConnectorFunctionTest {

  @Mock private ClassificationService classificationService;

  private ClassificationConnectorFunction connectorFunction;

  @BeforeEach
  void setUp() {
    connectorFunction = new ClassificationConnectorFunction();
    // Use reflection to inject the mock service
    try {
      var field = ClassificationConnectorFunction.class.getDeclaredField("classificationService");
      field.setAccessible(true);
      field.set(connectorFunction, classificationService);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock", e);
    }
  }

  @Test
  void execute_ShouldCallClassificationService_WithCorrectParameters() throws Exception {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "extractor": {
                    "type": "textract",
                    "awsAuthType": "credentials",
                    "accessKey": "test-key",
                    "secretKey": "test-secret",
                    "region": "us-east-1",
                    "bucketName": "test-bucket"
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
                    "categories": ["invoice", "receipt", "contract"]
                  }
                }
                """)
            .build();

    var expectedResult = new ClassificationResult("invoice", "0.95", "reasoning text", null);

    when(classificationService.execute(any(ClassificationRequest.class)))
        .thenReturn(expectedResult);

    // when
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    assertThat(((ClassificationResult) result).extractedValue()).isEqualTo("invoice");
    assertThat(((ClassificationResult) result).confidence()).isEqualTo("0.95");
    verify(classificationService).execute(any(ClassificationRequest.class));
  }

  @Test
  void execute_ShouldHandleMultipleCategories() throws Exception {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "extractor": {
                    "type": "textract",
                    "awsAuthType": "credentials",
                    "accessKey": "test-key",
                    "secretKey": "test-secret",
                    "region": "us-east-1",
                    "bucketName": "test-bucket"
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
                    "categories": ["invoice", "purchase_order", "receipt", "contract", "other"]
                  }
                }
                """)
            .build();

    var expectedResult = new ClassificationResult("contract", "0.88", "reasoning text", null);

    when(classificationService.execute(any(ClassificationRequest.class)))
        .thenReturn(expectedResult);

    // when
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    assertThat(((ClassificationResult) result).extractedValue()).isEqualTo("contract");
    verify(classificationService).execute(any(ClassificationRequest.class));
  }

  @Test
  void execute_ShouldReturnLowConfidenceClassification() throws Exception {
    // given
    var outBoundContext =
        OutboundConnectorContextBuilder.create()
            .variables(
                """
                {
                  "extractor": {
                    "type": "textract",
                    "awsAuthType": "credentials",
                    "accessKey": "test-key",
                    "secretKey": "test-secret",
                    "region": "us-east-1",
                    "bucketName": "test-bucket"
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
                    "categories": ["invoice", "receipt"]
                  }
                }
                """)
            .build();

    var expectedResult = new ClassificationResult("invoice", "0.45", "reasoning text", null);

    when(classificationService.execute(any(ClassificationRequest.class)))
        .thenReturn(expectedResult);

    // when
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    assertThat(Double.parseDouble(((ClassificationResult) result).confidence())).isLessThan(0.5);
    verify(classificationService).execute(any(ClassificationRequest.class));
  }
}
