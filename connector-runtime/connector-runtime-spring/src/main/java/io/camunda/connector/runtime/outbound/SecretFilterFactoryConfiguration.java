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

import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionSecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecretFilterFactoryConfiguration {
  @Bean
  public CacheManager secretKeyCacheManager(
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new NoOpCacheManager();
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(SecretKeyCache.SECRET_KEY_CACHE_NAME);
    cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(boundedMaxSize));
    return cacheManager;
  }

  @Bean
  public SecretKeyCache secretKeyCache(
      CamundaClient camundaClient, @Qualifier("secretKeyCacheManager") CacheManager cacheManager) {
    return new ProcessDefinitionSecretKeyCache(
        camundaClient, cacheManager.getCache(SecretKeyCache.SECRET_KEY_CACHE_NAME));
  }

  @Bean
  public SecretFilterFactory secretFilterFactory(
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode,
      SecretKeyCache secretKeyCache) {
    return new ConfigurableSecretFilterFactory(secretFilterMode, secretKeyCache);
  }
}
