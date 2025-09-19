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

import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.service.StructuredService;
import io.camunda.connector.idp.extraction.service.UnstructuredService;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
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
    var outBoundContext = prepareConnectorContext();
    var expectedResult = new ExtractionResult(Map.of("sum", "$12.25", "supplier", "Camunda Inc."));
    when(unstructuredService.extract(any(ExtractionRequest.class))).thenReturn(expectedResult);

    // when
    var result = extractionConnectorFunction.execute(outBoundContext);

    // then
    assertThat(result).isEqualTo(expectedResult);
    verify(unstructuredService).extract(any(ExtractionRequest.class));
  }

  private OutboundConnectorContextBuilder.TestConnectorContext prepareConnectorContext() {
    return OutboundConnectorContextBuilder.create()
        .secret("ACCESS_KEY", ExtractionTestUtils.ACTUAL_ACCESS_KEY)
        .secret("SECRET_KEY", ExtractionTestUtils.ACTUAL_SECRET_KEY)
        .variables(ExtractionTestUtils.TEXTRACT_EXTRACTION_INPUT_JSON)
        .build();
  }
}
