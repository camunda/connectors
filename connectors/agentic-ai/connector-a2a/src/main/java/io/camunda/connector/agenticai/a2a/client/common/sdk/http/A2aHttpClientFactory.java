/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.http;

import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import java.net.http.HttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;

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
                    // the SDK client follows redirects itself and rejects native redirects
                    .followRedirects(HttpClient.Redirect.NEVER))
            .build();

    return new JdkA2AHttpClient(httpClient);
  }
}
