/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.networking.baseurls.BaseUrls;
import com.box.sdkgen.networking.boxnetworkclient.BoxNetworkClient;
import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.ConnectionSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BoxNetworkSessionProxyTest {

  private final List<String> proxyAuthHeaders = new CopyOnWriteArrayList<>();
  private HttpServer proxy;

  @BeforeEach
  void startProxy() throws IOException {
    proxy = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    proxy.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          String auth = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
          proxyAuthHeaders.add(String.valueOf(auth));
          if (auth == null) {
            exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"test\"");
            exchange.sendResponseHeaders(407, -1);
          } else {
            byte[] body = "{\"id\":\"1\",\"type\":\"file\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    proxy.start();
  }

  @AfterEach
  void stopProxy() {
    proxy.stop(0);
  }

  @Test
  void routesRequestsThroughTheConfiguredProxyAndAuthenticates() {
    ProxyConfiguration configuration =
        protocol ->
            Optional.of(
                new ProxyDetails(
                    "http", "localhost", proxy.getAddress().getPort(), "user", "secret"));

    var file = clientVia(configuration).files.getFileById("1");

    assertThat(file.getId()).isEqualTo("1");
    // unreachable host: only the proxy can have answered; first try is challenged with a 407
    assertThat(proxyAuthHeaders).containsExactly("null", "Basic dXNlcjpzZWNyZXQ=");
  }

  private static BoxClient clientVia(ProxyConfiguration configuration) {
    String base = "http://box.invalid";
    var session =
        BoxNetworkSession.create(
                BoxNetworkClient.getDefaultOkHttpClientBuilder()
                    .connectionSpecs(List.of(ConnectionSpec.CLEARTEXT)),
                configuration)
            .withCustomBaseUrls(
                new BaseUrls.Builder().baseUrl(base + "/api").uploadUrl(base + "/upload").build());
    return new BoxClient.Builder(new BoxDeveloperTokenAuth("token"))
        .networkSession(session)
        .build();
  }
}
