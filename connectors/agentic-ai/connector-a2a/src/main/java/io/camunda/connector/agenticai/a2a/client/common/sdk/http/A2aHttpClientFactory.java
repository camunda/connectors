/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.http;

import io.a2a.client.http.A2AHttpClient;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.net.http.HttpClient;

/** Factory for creating proxy-configured {@link A2AHttpClient} instances. */
public class A2aHttpClientFactory {

  private final AgenticAiHttpProxySupport proxySupport;

  public A2aHttpClientFactory(AgenticAiHttpProxySupport proxySupport) {
    this.proxySupport = proxySupport;
  }

  public A2AHttpClient createHttpClient() {
    HttpClient httpClient =
        proxySupport
            .getJdkHttpClientProxyConfigurator()
            .configure(
                HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .followRedirects(HttpClient.Redirect.NORMAL))
            .build();

    return new CustomJdkA2AHttpClient(httpClient);
  }
}
