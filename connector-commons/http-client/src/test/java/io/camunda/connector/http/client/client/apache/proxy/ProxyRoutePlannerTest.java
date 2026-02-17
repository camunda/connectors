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
package io.camunda.connector.http.client.client.apache.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProxyRoutePlannerTest {

  HttpHost proxy = new HttpHost("theproxyhost", 4750);

  private static Stream<Arguments> provideNonProxyHostTestData() {
    return Stream.of(
        // nonProxyHosts, requestUrl, skipProxy
        Arguments.of("example.de", "http://example.de", true),
        Arguments.of("example.de", "http://www.example.de", false),
        Arguments.of("www.example.de", "http://www.example.de", true),
        Arguments.of("example.de|www.camunda.de", "http://www.camunda.io", false),
        Arguments.of("example.de", "http://www.example.de", false),
        Arguments.of("example.de", "http://api.example.de", false),
        Arguments.of("example.de", "http://another.example.de", false),
        Arguments.of("example.de", "http://example.com", false),
        Arguments.of("example.de|camunda.de", "http://www.example.de", false),
        Arguments.of("example.de|camunda.de", "http://www.camunda.de", false),
        Arguments.of("example.de|camunda.de", "http://www.google.de", false),
        Arguments.of("*.example.de", "http://api.example.de", true),
        Arguments.of("*.example.de", "http://www.example.de", true),
        Arguments.of("*.example.de", "http://example.de", false),
        Arguments.of("*.example.de", "http://example.com", false),
        Arguments.of("*.example.de|*.camunda.io", "http://sub.example.de", true),
        Arguments.of("*.example.de|*.camunda.io", "http://api.camunda.io", true),
        Arguments.of("*.example.de|*.camunda.io", "http://www.google.com", false));
  }

  @AfterEach
  public void clearSystemProperties() {
    System.clearProperty("http.nonProxyHosts");
  }

  @ParameterizedTest
  @MethodSource("provideNonProxyHostTestData")
  public void shouldUseProxy_whenRequestUrlMatches(
      String nonProxyHosts, String requestUrl, Boolean skipProxy)
      throws HttpException, URISyntaxException {
    // given
    HttpHost target = HttpHost.create(requestUrl);
    System.setProperty("http.nonProxyHosts", nonProxyHosts);
    ProxyRoutePlanner proxyRoutePlanner = new ProxyRoutePlanner(proxy);
    // Tests with the Java default proxy selector
    System.setProperty("http.proxyHost", "theproxyhost");
    System.setProperty("http.proxyPort", "4750");
    var javaDefaultProxies = ProxySelector.getDefault().select(URI.create(requestUrl));

    // when
    var route = proxyRoutePlanner.determineProxy(target, null);

    // then
    assertThat(javaDefaultProxies.size()).isEqualTo(1);
    var javaProxy = javaDefaultProxies.get(0);

    if (skipProxy) {
      assertThat(route).isNull();
      assertThat(javaProxy.type()).isEqualTo(Proxy.Type.DIRECT);
    } else {
      assertThat(route).isNotNull();
      assertThat(route).isEqualTo(proxy);
      assertThat(javaProxy.type()).isEqualTo(Proxy.Type.HTTP);
    }
  }
}
