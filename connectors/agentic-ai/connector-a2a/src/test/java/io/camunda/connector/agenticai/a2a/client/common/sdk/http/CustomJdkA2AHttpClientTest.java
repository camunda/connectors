/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class CustomJdkA2AHttpClientTest {

  @Test
  void defaultConstructorCreatesClient() {
    var client = new CustomJdkA2AHttpClient();
    assertThat(client.createGet()).isNotNull();
    assertThat(client.createPost()).isNotNull();
    assertThat(client.createDelete()).isNotNull();
  }

  @Test
  void customHttpClientConstructorCreatesClient() {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    var client = new CustomJdkA2AHttpClient(httpClient);
    assertThat(client.createGet()).isNotNull();
    assertThat(client.createPost()).isNotNull();
    assertThat(client.createDelete()).isNotNull();
  }
}
