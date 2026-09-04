/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AgenticAiNativeProvidersConfigurationTest {

  private final AgenticAiNativeProvidersConfiguration configuration =
      new AgenticAiNativeProvidersConfiguration();
  private final AgenticAiHttpProxySupport httpProxySupport =
      new AgenticAiHttpProxySupport(ProxyConfiguration.NONE);

  @Mock private ObjectProvider<OAuthTokenCache> oAuthTokenCacheProvider;

  @AfterEach
  void resetHolder() {
    // restore a fresh default so this test doesn't leak its custom cache into other tests
    OAuthTokenCacheHolder.set(new CaffeineOAuthTokenCache());
  }

  @Test
  void shouldRegisterCustomOAuthTokenCacheBeanIntoHolder() {
    final var customCache = new CaffeineOAuthTokenCache();
    when(oAuthTokenCacheProvider.getIfAvailable(any())).thenReturn(customCache);

    configuration.aiAgentOAuthClientCredentialsTokenResolver(
        oAuthTokenCacheProvider, httpProxySupport);

    assertThat(OAuthTokenCacheHolder.get()).isSameAs(customCache);
  }
}
