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

import com.github.benmanes.caffeine.cache.Cache;
import java.util.List;
import java.util.Map;

/**
 * Wraps the secret-key {@link Cache} so the bean exposed to the Spring context is never itself a
 * {@code Cache}: an unqualified {@code Cache}-typed bean would collide with a host application's
 * own cache bean of that type (or be silently injected into one of the host's own unqualified
 * {@code Cache} injection points), the same problem this replaces at the {@code CacheManager}
 * level. Package-private so nothing outside this configuration class can declare or depend on this
 * exact type either.
 *
 * <p>Uses Caffeine's own {@code Cache}, not Spring's {@code org.springframework.cache.Cache}: the
 * Spring wrapper types live in {@code spring-context-support}, which also carries {@code
 * JCacheCacheManager}. Combined with the {@code cache-api} JAR this runtime already pulls in
 * transitively (via the Operate client's {@code ehcache} dependency), putting that class on a
 * host's classpath satisfies Spring Boot's JCache auto-configuration conditions and can silently
 * replace the host's own auto-configured {@code CacheManager} — confirmed via {@code
 * dependency:tree}, not theoretical. Caffeine's {@code Cache} needs neither dependency.
 */
record SecretKeyCacheHolder(Cache<Long, Map<String, List<String>>> cache) {}
