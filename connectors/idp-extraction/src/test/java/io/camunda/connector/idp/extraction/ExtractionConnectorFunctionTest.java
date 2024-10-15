/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionConnectorFunctionTest {

    @Mock
    private PollingTextractCaller pollingTextractCaller;

    @Mock
    private BedrockCaller bedrockCaller;

    @InjectMocks
    private ExtractionConnectorFunction extractionConnectorFunction;

    @Test
    void executeExtractionReturnsCorrectResult() throws Exception {
        var outBounderContext = prepareConnectorContext(ExtractionTestUtils.TEXTRACT_EXTRACTION_INPUT_JSON);

        when(pollingTextractCaller.call(any(), any(), any(), any())).thenReturn("Test extracted text from test document.pdf");
        when(bedrockCaller.call(any(), any(), any())).thenReturn(
                """
                        {
                        	"name": "John Smith",
                        	"age": 32
                        }
                        """
        );

        var result = extractionConnectorFunction.execute(outBounderContext);
        assertThat(result).isInstanceOf(ExtractionResult.class);
    }

    private OutboundConnectorContextBuilder.TestConnectorContext prepareConnectorContext(
            String json) {
        return OutboundConnectorContextBuilder.create()
                .secret("ACCESS_KEY", ExtractionTestUtils.ACTUAL_ACCESS_KEY)
                .secret("SECRET_KEY", ExtractionTestUtils.ACTUAL_SECRET_KEY)
                .variables(json)
                .build();
    }
}
