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
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResult;
import io.camunda.connector.idp.extraction.service.StructuredService;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StructuredExtractionConnectorFunctionTest {

  @Mock private StructuredService structuredService;

  private StructuredExtractionConnectorFunction connectorFunction;

  @BeforeEach
  void setUp() {
    connectorFunction = new StructuredExtractionConnectorFunction();
    // Use reflection to inject the mock service
    try {
      var field = StructuredExtractionConnectorFunction.class.getDeclaredField("structuredService");
      field.setAccessible(true);
      field.set(connectorFunction, structuredService);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock", e);
    }
  }

  @Test
  void execute_ShouldCallStructuredService_WithCorrectParameters() throws Exception {
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
                  "input": {
                    "document": {
                      "camunda.document.type": "camunda",
                      "storeId": "test",
                      "documentId": "test",
                      "metadata": {}
                    },
                    "includedFields": ["Invoice Number", "Total Amount"],
                    "renameMappings": {"Invoice Number": "invoiceNum"},
                    "delimiter": ","
                  }
                }
                """)
            .build();

    var expectedResult =
        new StructuredExtractionResult(
            Map.of("invoiceNum", "INV-12345", "Total Amount", "1000.00"),
            Map.of("invoiceNum", 0.95, "Total Amount", 0.88),
            Map.of(),
            Map.of());

    when(structuredService.extract(
            any(MlExtractor.class), anyList(), anyMap(), anyString(), any(Document.class)))
        .thenReturn(expectedResult);

    // when
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    verify(structuredService)
        .extract(any(MlExtractor.class), anyList(), anyMap(), anyString(), any(Document.class));
  }

  @Test
  void execute_ShouldHandleEmptyRenameMappings() throws Exception {
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
                  "input": {
                    "document": {
                      "camunda.document.type": "camunda",
                      "storeId": "test",
                      "documentId": "test",
                      "metadata": {}
                    },
                    "includedFields": ["field1"],
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
    var result = connectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    verify(structuredService)
        .extract(any(MlExtractor.class), anyList(), anyMap(), anyString(), any(Document.class));
  }
}
