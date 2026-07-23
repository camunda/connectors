/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenAiOkHttpClientFactoryTest {

  private final HttpTransportSupport transport = mock(HttpTransportSupport.class);

  @Test
  void buildsDirectClient() {
    var backend = new OpenAiDirectBackend("k", "org", "proj");
    var client = new OpenAiOkHttpClientFactory(backend, Duration.ofSeconds(30), transport).create();
    assertThat(client).isNotNull();
  }

  @Test
  void buildsCompatibleClientWithEndpoint() {
    var backend =
        new OpenAiCompatibleBackend(
            "https://example.test/v1", null, null, null, new CompatibleApiKeyAuthentication("k"));
    var client = new OpenAiOkHttpClientFactory(backend, null, transport).create();
    assertThat(client).isNotNull();
  }
}
