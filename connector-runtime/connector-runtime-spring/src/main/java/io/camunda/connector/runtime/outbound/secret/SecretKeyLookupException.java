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
package io.camunda.connector.runtime.outbound.secret;

/**
 * Wraps a checked failure from the Operate lookup so it can cross a {@code
 * com.github.benmanes.caffeine.cache.Cache#get(Object, java.util.function.Function)} boundary,
 * whose mapping function may not declare checked exceptions. This is the only wrapper in the lookup
 * path — Caffeine, unlike Spring's {@code Cache#get(Object, Callable)}, rethrows an unchecked
 * mapping-function failure unwrapped, so every other exception on this path reaches the caller
 * exactly as thrown.
 */
public class SecretKeyLookupException extends RuntimeException {
  public SecretKeyLookupException(String message, Throwable cause) {
    super(message, cause);
  }
}
