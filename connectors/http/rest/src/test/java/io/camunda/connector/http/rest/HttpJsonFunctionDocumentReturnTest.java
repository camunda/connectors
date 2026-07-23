/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.DocumentReturnFormat;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link HttpJsonFunction} takes the {@link DocumentReturn} flow when a {@code
 * documentReturnFormat} choice is present, and the legacy path when absent.
 */
@WireMockTest
class HttpJsonFunctionDocumentReturnTest {

  private final HttpJsonFunction function = new HttpJsonFunction();

  private HttpJsonRequest request(WireMockRuntimeInfo wm) {
    HttpJsonRequest request = new HttpJsonRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(wm.getHttpBaseUrl() + "/endpoint");
    return request;
  }

  @Test
  void choicePresent_takesNewPath_returnsDocumentReturn(WireMockRuntimeInfo wm) {
    stubFor(get("/endpoint").willReturn(ok("{}")));
    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(HttpJsonRequest.class)).thenReturn(request(wm));
    when(context.readDocumentReturnFormat())
        .thenReturn(Optional.of(new DocumentReturnFormat(DocumentReturnChoice.JSON, null)));

    Object result = function.execute(context);

    assertThat(result).isInstanceOf(DocumentReturn.class);
  }

  @Test
  void choiceAbsent_takesLegacyPath_returnsHttpCommonResult(WireMockRuntimeInfo wm) {
    stubFor(
        get("/endpoint")
            .willReturn(ok("{\"k\":\"v\"}").withHeader("Content-Type", "application/json")));
    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    HttpJsonRequest request = request(wm);
    request.setStoreResponse(false);
    when(context.bindVariables(HttpJsonRequest.class)).thenReturn(request);
    when(context.readDocumentReturnFormat()).thenReturn(Optional.empty());

    Object result = function.execute(context);

    assertThat(result).isInstanceOf(HttpCommonResult.class);
    assertThat(((HttpCommonResult) result).body()).isEqualTo(Map.of("k", "v"));
  }
}
