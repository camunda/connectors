/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.http.graphql.GraphQLFunction.GRAPHQL_ERROR_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.DocumentReturnFormat;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Covers GraphQL error detection on the response-format path, where it runs inside the wrap lambda.
 */
@WireMockTest
class GraphQLFunctionDocumentReturnTest {

  private final GraphQLFunction function = new GraphQLFunction();

  private OutboundConnectorContext contextWithChoice(
      WireMockRuntimeInfo wm, DocumentReturnChoice choice) {
    var graphql =
        new GraphQLRequest.GraphQL(
            "{ hero { name } }",
            null,
            HttpMethod.POST,
            wm.getHttpBaseUrl() + "/graphql",
            null,
            false,
            20,
            20);
    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(GraphQLRequest.class)).thenReturn(new GraphQLRequest(graphql, null));
    when(context.readDocumentReturnFormat())
        .thenReturn(Optional.of(new DocumentReturnFormat(choice, null)));
    return context;
  }

  @Test
  void newPath_returnsDocumentReturn(WireMockRuntimeInfo wm) {
    stubFor(any(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(200)));

    Object result = function.execute(contextWithChoice(wm, DocumentReturnChoice.JSON));

    assertThat(result).isInstanceOf(DocumentReturn.class);
  }

  @Test
  void newPath_wrapDetectsGraphQLErrors_andThrows(WireMockRuntimeInfo wm) {
    stubFor(any(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(200)));

    var documentReturn =
        (DocumentReturn<?>) function.execute(contextWithChoice(wm, DocumentReturnChoice.JSON));

    Map<String, Object> errorBody = Map.of("errors", List.of(Map.of("message", "boom")));

    assertThatThrownBy(() -> documentReturn.wrap().apply(errorBody, DocumentReturnChoice.JSON))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            ex -> {
              var ce = (ConnectorException) ex;
              assertThat(ce.getErrorCode()).isEqualTo(GRAPHQL_ERROR_CODE);
              assertThat(ce.getMessage()).isEqualTo("boom");
            });
  }

  @Test
  void newPath_wrapReturnsResult_whenNoErrors(WireMockRuntimeInfo wm) {
    stubFor(any(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(200)));

    var documentReturn =
        (DocumentReturn<?>) function.execute(contextWithChoice(wm, DocumentReturnChoice.JSON));

    Map<String, Object> dataBody = Map.of("data", Map.of("hero", Map.of("name", "R2-D2")));
    Object wrapped = documentReturn.wrap().apply(dataBody, DocumentReturnChoice.JSON);

    assertThat(wrapped).isInstanceOf(HttpCommonResult.class);
    assertThat(((HttpCommonResult) wrapped).body()).isEqualTo(dataBody);
  }

  @Test
  void newPath_text_wrapDetectsGraphQLErrorsInDecodedText_andThrows(WireMockRuntimeInfo wm) {
    stubFor(any(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(200)));

    var documentReturn =
        (DocumentReturn<?>) function.execute(contextWithChoice(wm, DocumentReturnChoice.TEXT));

    // With TEXT the runtime hands the wrap lambda the decoded body as a String.
    String errorText = "{\"errors\":[{\"message\":\"boom\"}]}";

    assertThatThrownBy(() -> documentReturn.wrap().apply(errorText, DocumentReturnChoice.TEXT))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            ex -> {
              var ce = (ConnectorException) ex;
              assertThat(ce.getErrorCode()).isEqualTo(GRAPHQL_ERROR_CODE);
              assertThat(ce.getMessage()).isEqualTo("boom");
            });
  }

  @Test
  void newPath_text_nonJsonBodyReturnedUnchanged(WireMockRuntimeInfo wm) {
    stubFor(any(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(200)));

    var documentReturn =
        (DocumentReturn<?>) function.execute(contextWithChoice(wm, DocumentReturnChoice.TEXT));

    Object wrapped = documentReturn.wrap().apply("just plain text", DocumentReturnChoice.TEXT);

    assertThat(wrapped).isInstanceOf(HttpCommonResult.class);
    assertThat(((HttpCommonResult) wrapped).body()).isEqualTo("just plain text");
  }
}
