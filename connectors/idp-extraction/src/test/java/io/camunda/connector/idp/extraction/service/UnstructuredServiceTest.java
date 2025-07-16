/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.caller.AzureAiFoundryCaller;
import io.camunda.connector.idp.extraction.caller.AzureDocumentIntelligenceCaller;
import io.camunda.connector.idp.extraction.caller.BedrockCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.caller.VertexCaller;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.supplier.BedrockRuntimeClientSupplier;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UnstructuredServiceTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock private PollingTextractCaller pollingTextractCaller;
  @Mock private BedrockCaller bedrockCaller;
  @Mock private AzureAiFoundryCaller azureAiFoundryCaller;
  @Mock private AzureDocumentIntelligenceCaller azureDocumentIntelligenceCaller;

  private UnstructuredService unstructuredService;

  @BeforeEach
  void setUp() {
    TextractClientSupplier textractClientSupplier = new TextractClientSupplier();
    S3ClientSupplier s3ClientSupplier = new S3ClientSupplier();
    BedrockRuntimeClientSupplier bedrockRuntimeClientSupplier = new BedrockRuntimeClientSupplier();
    ObjectMapper objectMapper = new ObjectMapper();
    VertexCaller vertexCaller = new VertexCaller();

    // Initialize service with constructor injection
    unstructuredService =
        new UnstructuredService(
            textractClientSupplier,
            s3ClientSupplier,
            bedrockRuntimeClientSupplier,
            pollingTextractCaller,
            bedrockCaller,
            vertexCaller,
            objectMapper,
            azureAiFoundryCaller,
            azureDocumentIntelligenceCaller);
  }

  @Test
  void extractUsingAws_ReturnsCorrectResult() throws Exception {
    // given
    var request = prepareExtractionRequest();
    var expectedResponseJson =
        """
            {
            	"sum": "$12.25",
            	"supplier": "Camunda Inc."
            }
        """;

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any())).thenReturn(expectedResponseJson);

    // when
    var result = unstructuredService.extract(request);

    // then
    assertExtractionResult(result, expectedResponseJson);
  }

  @Test
  void
      extractUsingAws_ReturnsPartiallyCorrectResult_whenLlmResponseIsMissingValueForSomeTaxonomyItems()
          throws Exception {
    // given
    var request = prepareExtractionRequest();
    var expectedResponseJson =
        """
            {
            	"sum": "$12.25"
            }
        """;

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any())).thenReturn(expectedResponseJson);

    // when
    var result = unstructuredService.extract(request);

    // then
    assertExtractionResult(result, expectedResponseJson);
  }

  @Test
  void extractUsingAws_ReturnsCorrectResult_whenLlmResponseContainsNestedResponseObject()
      throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                   {
                     "response": {
                                   "sum": "$12.25",
                                   "supplier": "Camunda Inc."
                                 }
                   }
               """);

    // when
    var result = unstructuredService.extract(request);

    // then
    var expectedResponseJson =
        """
            {
              "sum": "$12.25",
              "supplier": "Camunda Inc."
            }
        """;
    assertExtractionResult(result, expectedResponseJson);
  }

  @Test
  void extractUsingAws_ReturnsCorrectResult_whenLlmResponseContainsObjectAsValueForTaxonomyItem()
      throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                   {
                     "sum": "$12.25",
                     "supplier": {
                                   "id": 12345,
                                   "name": "Camunda Inc.",
                                   "city": "Berlin",
                                   "country": "Germany"
                                 }
                   }
               """);

    // when
    var result = unstructuredService.extract(request);

    // then
    var expectedResponseJson =
        """
            {
              "sum": "$12.25",
              "supplier": {
                            "id": 12345,
                            "name": "Camunda Inc.",
                            "city": "Berlin",
                            "country": "Germany"
                          }
            }
        """;
    assertExtractionResult(result, expectedResponseJson);
  }

  @Test
  void
      extractUsingAws_ReturnsCorrectResult_whenLlmResponseContainsNestedObjectAsValueForTaxonomyItem()
          throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                   {
                     "response": {
                                   "sum": "$12.25",
                                   "supplier": {
                                                 "id": 12345,
                                                 "name": "Camunda Inc.",
                                                 "city": "Berlin",
                                                 "country": "Germany"
                                               }
                                 }
                   }
               """);

    // when
    var result = unstructuredService.extract(request);

    // then
    var expectedResponseJson =
        """
            {
              "sum": "$12.25",
              "supplier": {
                            "id": 12345,
                            "name": "Camunda Inc.",
                            "city": "Berlin",
                            "country": "Germany"
                          }
            }
        """;
    assertExtractionResult(result, expectedResponseJson);
  }

  @Test
  void
      extractUsingAws_ReturnsCorrectResult_whenLlmResponseContainsNestedResponseWithValidStringifiedObject()
          throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                   {
                     "response": "\\n\\n{\\"sum\\":\\"$12.25\\",\\"supplier\\":\\"Camunda Inc.\\"}"
                   }
               """);

    // when
    var result = unstructuredService.extract(request);

    // then
    var expectedResponseJson =
        """
            {
            	"sum": "$12.25",
            	"supplier": "Camunda Inc."
            }
        """;
    assertExtractionResult(result, expectedResponseJson);
  }

  @Test
  void
      extractUsingAws_ShouldThrowConnectorException_whenLlmResponseContainsInvalidStringifiedObject()
          throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                        {
                        """);

    // when & then
    assertThatThrownBy(() -> unstructuredService.extract(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to parse the JSON response");
  }

  @Test
  void extractUsingAws_ShouldThrowConnectorException_whenLlmResponseIsNotJsonObject()
      throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                        []
                        """);

    // when & then
    assertThatThrownBy(() -> unstructuredService.extract(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("LLM response is not a JSON object");
  }

  @Test
  void
      extractUsingAws_ShouldThrowConnectorException_whenLlmResponseContainsNestedResponseWithInvalidStringifiedObject()
          throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                        {
                          "response": "{"
                        }
                        """);

    // when & then
    assertThatThrownBy(() -> unstructuredService.extract(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to parse the JSON response");
  }

  @Test
  void
      extractUsingAws_ShouldThrowConnectorException_whenLlmResponseContainsNestedResponseWithJsonArray()
          throws Exception {
    // given
    var request = prepareExtractionRequest();

    when(pollingTextractCaller.call(any(), any(), any(), any()))
        .thenReturn("Test extracted text from test document.pdf");
    when(bedrockCaller.call(any(), any(), any(), any()))
        .thenReturn(
            """
                        {
                          "response": []
                        }
                        """);

    // when & then
    assertThatThrownBy(() -> unstructuredService.extract(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("LLM response is neither a JSON object nor a string");
  }

  private void assertExtractionResult(Object result, String expectedResponse)
      throws JsonProcessingException {
    var expected =
        OBJECT_MAPPER.convertValue(
            OBJECT_MAPPER.readTree(expectedResponse),
            new TypeReference<Map<String, JsonNode>>() {});

    assertThat(result)
        .isNotNull()
        .isInstanceOf(ExtractionResult.class)
        .asInstanceOf(InstanceOfAssertFactories.type(ExtractionResult.class))
        .satisfies(
            extractionResult ->
                assertThat(extractionResult.extractedFields())
                    .containsExactlyInAnyOrderEntriesOf(expected));
  }

  private ExtractionRequest prepareExtractionRequest() {
    AwsProvider baseRequest = ExtractionTestUtils.createDefaultAwsProvider();
    return new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);
  }
}
