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
package io.camunda.connector.http.base.components.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

@WireMockTest
public class CustomApacheHttpClientTest {

  CustomApacheHttpClient customApacheHttpClient = CustomApacheHttpClient.getDefault();

  @Test
  public void testCreateHttpClientBuilder(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    stubFor(
        get("/static-dsl")
            .withHeader("Accept", containing(ContentType.APPLICATION_JSON.getMimeType()))
            .willReturn(ok()));
    int port = wmRuntimeInfo.getHttpPort();

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    // request.setHeaders(Map.of("Content-Type", ContentType.TEXT_PLAIN.getMimeType()));
    request.setUrl("http://localhost:" + port + "/static-dsl");
    HttpCommonResult result = customApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
  }
}
