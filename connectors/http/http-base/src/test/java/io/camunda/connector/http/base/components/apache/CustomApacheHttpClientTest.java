/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import java.util.Map;
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
    request.setHeaders(Map.of("Content-Type", ContentType.TEXT_PLAIN.getMimeType()));
    request.setUrl("http://localhost:" + port + "/static-dsl");
    HttpCommonResult result = customApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
  }
}
