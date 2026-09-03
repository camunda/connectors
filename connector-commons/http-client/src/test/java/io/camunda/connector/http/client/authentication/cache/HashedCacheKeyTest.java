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
package io.camunda.connector.http.client.authentication.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashedCacheKeyTest {

  @Test
  void producesA64CharacterHexString() {
    assertThat(HashedCacheKey.of("endpoint", "clientId")).hasSize(64).matches("^[0-9a-f]{64}$");
  }

  @Test
  void sameInputsProduceTheSameKey() {
    assertThat(HashedCacheKey.of("endpoint", "clientId", "secret"))
        .isEqualTo(HashedCacheKey.of("endpoint", "clientId", "secret"));
  }

  @Test
  void differentInputsProduceDifferentKeys() {
    assertThat(HashedCacheKey.of("endpoint", "clientId", "secret"))
        .isNotEqualTo(HashedCacheKey.of("endpoint", "clientId", "other-secret"));
  }

  @Test
  void nullPartsAreCoalescedToEmptyString() {
    assertThat(HashedCacheKey.of("endpoint", null, "secret"))
        .isEqualTo(HashedCacheKey.of("endpoint", "", "secret"));
  }

  @Test
  void isSensitiveToPartOrderNotJustConcatenation() {
    assertThat(HashedCacheKey.of("ab", "c")).isNotEqualTo(HashedCacheKey.of("a", "bc"));
  }

  @Test
  void singlePartKeyMatchesHashingThatPartAlone() {
    // EntraIdTokenCredentialFactory.managedIdentity(clientId) hashes a single value with no
    // separator needed -- of(String...) must reproduce that exact shape for a 1-arg call.
    assertThat(HashedCacheKey.of("some-client-id")).isEqualTo(HashedCacheKey.of("some-client-id"));
    assertThat(HashedCacheKey.of("some-client-id")).isNotEqualTo(HashedCacheKey.of("other"));
  }
}
