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
package io.camunda.connector.runtime.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCache;

class SecretFilterFactoryConfigurationTest {

  private final SecretFilterFactoryConfiguration configuration =
      new SecretFilterFactoryConfiguration();

  @Test
  void secretKeyCacheStore_whenEnabled_returnsCaffeineCache() {
    var cache = configuration.secretKeyCacheStore(true, 1000).cache();

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenDisabled_returnsNoOpCache() {
    var cache = configuration.secretKeyCacheStore(false, 1000).cache();

    assertInstanceOf(NoOpCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenDisabled_cacheNeverStoresValues() {
    Cache cache = configuration.secretKeyCacheStore(false, 1000).cache();

    var callCount = new AtomicInteger(0);
    cache.get("key", callCount::incrementAndGet);
    cache.get("key", callCount::incrementAndGet);

    assertEquals(2, callCount.get(), "NoOp cache must call loader on every get");
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsZero_clampedToDefault() {
    var cache = configuration.secretKeyCacheStore(true, 0).cache();

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsNegative_clampedToDefault() {
    var cache = configuration.secretKeyCacheStore(true, -1).cache();

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_beanTypeIsNotAPlainCache_soItCannotCollideWithAHostCacheBean() {
    // Regression: the bean previously returned a plain Cache, which collided with a host
    // application's own unqualified Cache bean the same way an unqualified CacheManager bean
    // used to. The holder type is what makes that impossible now, not the @Qualifier.
    var holder = configuration.secretKeyCacheStore(true, 1000);

    assertInstanceOf(SecretKeyCacheHolder.class, holder);
  }

  @Test
  void secretKeyCacheAndIntrinsicFunctionAllowListCache_shareOneBpmnModelCacheStore_fetchOnce()
      throws IOException {
    // Regression for Finding C: before this, secretKeyCache() fetched and parsed a process
    // definition's BPMN XML independently of intrinsicFunctionAllowListCache(), which already
    // shared its own separate model cache internally -- two consumers, two fetches for the same
    // process definition. Both beans now build their ProcessDefinitionModelCache from the same
    // bpmnModelCacheStore, so the first consumer to ask for a given process definition key is the
    // only one that ever calls out to Zeebe for it.
    var camundaClient = mock(CamundaClient.class);
    var xmlRequest = mock(ProcessDefinitionGetXmlRequest.class);
    when(camundaClient.newProcessDefinitionGetXmlRequest(anyLong())).thenReturn(xmlRequest);
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-secrets.bpmn"));
    var bpmnModelCacheStore = configuration.bpmnModelCacheStore(true, 1000);

    var secretKeyCache =
        configuration.secretKeyCache(
            camundaClient, configuration.secretKeyCacheStore(true, 1000), bpmnModelCacheStore);
    var intrinsicFunctionAllowListCache =
        configuration.intrinsicFunctionAllowListCache(
            camundaClient,
            bpmnModelCacheStore,
            configuration.intrinsicFunctionAllowListCacheStore(true, 1000));
    var deadline = Instant.now().plusSeconds(30);

    secretKeyCache.getSecretKeys(new SecretKeyContext(42L, "service-task-1", deadline));
    intrinsicFunctionAllowListCache.getAllowedFunctions(
        new IntrinsicFunctionAllowListContext(42L, "service-task-1", deadline));

    verify(camundaClient, times(1)).newProcessDefinitionGetXmlRequest(42L);
  }

  private static String loadBpmn(String fileName) throws IOException {
    try (var stream =
        SecretFilterFactoryConfigurationTest.class
            .getClassLoader()
            .getResourceAsStream("bpmn/" + fileName)) {
      if (stream == null) {
        throw new IllegalArgumentException("BPMN resource not found: bpmn/" + fileName);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
