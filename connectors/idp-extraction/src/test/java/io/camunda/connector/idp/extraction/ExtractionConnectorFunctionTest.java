/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionConnectorFunctionTest {

  @Mock private PollingTextractCaller pollingTextractCaller;

  @Mock private BedrockCaller bedrockCaller;

  @InjectMocks private ExtractionConnectorFunction extractionConnectorFunction;

  @Test
  void executeExtractionReturnsCorrectResult() throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                        	"sum": "$12.25",
                        	"supplier": "Camunda Inc."
                        }
                        """);

    var result = extractionConnectorFunction.execute(outBounderContext);
    assertExtractionResult(
        result, Map.of("sum", new TextNode("$12.25"), "supplier", new TextNode("Camunda Inc.")));
  }

  @Test
  void
      executeExtractionReturnsPartiallyCorrectResult_whenLlmResponseIsMissingValueForSomeTaxonomyItems()
          throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                        	"sum": "$12.25"
                        }
                        """);

    var result = extractionConnectorFunction.execute(outBounderContext);
    assertExtractionResult(result, Map.of("sum", new TextNode("$12.25")));
  }

  @Test
  void executeExtractionReturnsCorrectResult_whenLlmResponseContainsNestedResponseObject()
      throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                          "response": {
                                      "sum": "$12.25",
                                      "supplier": "Camunda Inc."
                                      }
                        }
                        """);

    var result = extractionConnectorFunction.execute(outBounderContext);
    assertExtractionResult(
        result, Map.of("sum", new TextNode("$12.25"), "supplier", new TextNode("Camunda Inc.")));
  }

  @Test
  void
      executeExtractionReturnsCorrectResult_whenLlmResponseContainsNestedResponseWithValidStringifiedObject()
          throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                          "response": "\\n\\n{\\"sum\\":\\"$12.25\\",\\"supplier\\":\\"Camunda Inc.\\"}"
                        }
                        """);

    var result = extractionConnectorFunction.execute(outBounderContext);
    assertExtractionResult(
        result, Map.of("sum", new TextNode("$12.25"), "supplier", new TextNode("Camunda Inc.")));
  }

  @Test
  void
      executeExtractionShouldThrowConnectorException_whenLlmResponseContainsInvalidStringifiedObject()
          throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                        """);

    assertThatThrownBy(() -> extractionConnectorFunction.execute(outBounderContext))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to parse the JSON response");
  }

  @Test
  void executeExtractionShouldThrowConnectorException_whenLlmResponseIsNotJsonObject()
      throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        []
                        """);

    assertThatThrownBy(() -> extractionConnectorFunction.execute(outBounderContext))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("LLM response is not a JSON object");
  }

  @Test
  void
      executeExtractionShouldThrowConnectorException_whenLlmResponseContainsNestedResponseWithInvalidStringifiedObject()
          throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                          "response": "{"
                        }
                        """);

    assertThatThrownBy(() -> extractionConnectorFunction.execute(outBounderContext))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to parse the JSON response");
  }

  @Test
  void
      executeExtractionShouldThrowConnectorException_whenLlmResponseContainsNestedResponseWithJsonArray()
          throws Exception {
    var outBounderContext = prepareConnectorContext();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any()))
        .thenReturn(
            """
                        {
                          "response": []
                        }
                        """);

    assertThatThrownBy(() -> extractionConnectorFunction.execute(outBounderContext))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("LLM response is neither a JSON object nor a string");
  }

  private void assertExtractionResult(Object result, Map<String, JsonNode> expectedResponse) {
    assertThat(result)
        .isNotNull()
        .isInstanceOf(ExtractionResult.class)
        .asInstanceOf(InstanceOfAssertFactories.type(ExtractionResult.class))
        .satisfies(
            extractionResult ->
                assertThat(extractionResult.response())
                    .containsExactlyInAnyOrderEntriesOf(expectedResponse));
  }

  private OutboundConnectorContextBuilder.TestConnectorContext prepareConnectorContext() {
    return OutboundConnectorContextBuilder.create()
        .secret("ACCESS_KEY", ExtractionTestUtils.ACTUAL_ACCESS_KEY)
        .secret("SECRET_KEY", ExtractionTestUtils.ACTUAL_SECRET_KEY)
        .variables(ExtractionTestUtils.TEXTRACT_EXTRACTION_INPUT_JSON)
        .build();
  }
}
