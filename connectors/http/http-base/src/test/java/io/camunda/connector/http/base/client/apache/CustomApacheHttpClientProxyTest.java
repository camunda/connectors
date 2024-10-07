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
package io.camunda.connector.http.base.client.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import wiremock.com.fasterxml.jackson.databind.node.POJONode;

@WireMockTest(httpPort = 28090)
public class CustomApacheHttpClientProxyTest {

  private static CustomApacheHttpClient proxiedApacheHttpClient;
  private static GenericContainer<?> proxyContainer;

  @BeforeAll
  public static void setUp() {
    proxyContainer =
        new GenericContainer<>(DockerImageName.parse("sameersbn/squid:3.5.27-2"))
            .withExposedPorts(3128)
            .withClasspathResourceMapping("squid.conf", "/etc/squid/squid.conf", BindMode.READ_ONLY)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());
    proxyContainer.start();

    // Set up the HttpClient to use the proxy
    String proxyHost = proxyContainer.getHost();
    Integer proxyPort = proxyContainer.getMappedPort(3128);
    System.setProperty("http.proxyHost", proxyHost);
    System.setProperty("http.proxyPort", proxyPort.toString());
    System.setProperty("http.nonProxyHosts", "");
    System.setProperty("https.proxyHost", proxyHost);
    System.setProperty("https.proxyPort", proxyPort.toString());
    System.setProperty("https.nonProxyHosts", "");
    proxiedApacheHttpClient = CustomApacheHttpClient.create(HttpClients.custom());
  }

  @AfterAll
  public static void tearDown() {
    proxyContainer.stop();
    System.setProperty("http.proxyHost", "");
    System.setProperty("http.proxyPort", "");
    System.setProperty("http.nonProxyHosts", "");
    System.setProperty("https.proxyHost", "");
    System.setProperty("https.proxyPort", "");
    System.setProperty("https.nonProxyHosts", "");
  }

  @Test
  public void shouldReturn200_whenGetAndProxySet() {
    stubFor(get("/path").willReturn(ok().withBody("Hello, world!")));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl("http://host.docker.internal:28090/path");
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("Hello, world!");
    assertThat(result.headers().get("Via")).asString().contains("squid");
    verify(getRequestedFor(urlEqualTo("/path")));
  }

  @Test
  public void shouldReturn200_whenPostAndProxySet() {
    stubFor(
        post("/path").willReturn(created().withJsonBody(new POJONode(Map.of("key1", "value1")))));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.POST);
    request.setUrl("http://host.docker.internal:28090/path");
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
    assertThat(result.headers().get("Via")).asString().contains("squid");
    verify(postRequestedFor(urlEqualTo("/path")));
  }

  @Test
  public void shouldReturn200_whenPutAndProxySet() {
    stubFor(put("/path").willReturn(ok().withJsonBody(new POJONode(Map.of("key1", "value1")))));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.PUT);
    request.setUrl("http://host.docker.internal:28090/path");
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
    assertThat(result.headers().get("Via")).asString().contains("squid");
    verify(putRequestedFor(urlEqualTo("/path")));
  }

  @Test
  public void shouldReturn200_whenDeleteAndProxySet() {
    stubFor(delete("/path").willReturn(noContent()));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.DELETE);
    request.setUrl("http://host.docker.internal:28090/path");
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(204);
    assertThat(result.headers().get("Via")).asString().contains("squid");
    verify(deleteRequestedFor(urlEqualTo("/path")));
  }
}
