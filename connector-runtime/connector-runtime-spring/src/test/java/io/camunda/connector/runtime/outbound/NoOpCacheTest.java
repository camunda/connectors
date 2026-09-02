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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoOpCacheTest {

  private final NoOpCache<String, String> cache = new NoOpCache<>();

  @Test
  void get_callsTheMappingFunctionOnEveryCall_neverMemoizing() {
    var callCount = new AtomicInteger(0);

    var first = cache.get("key", k -> "v" + callCount.incrementAndGet());
    var second = cache.get("key", k -> "v" + callCount.incrementAndGet());

    assertThat(first).isEqualTo("v1");
    assertThat(second).isEqualTo("v2");
  }

  @Test
  void getAll_stillInvokesTheMappingFunctionForTheFullKeySet() {
    // A disabled cache avoids retention, not loading -- a bulk lookup must still return the
    // loaded values, not an empty map, even though nothing gets stored afterward.
    var result =
        cache.getAll(
            List.of("a", "b"),
            keys ->
                keys.stream()
                    .collect(java.util.stream.Collectors.toMap(k -> k, k -> k + "-value")));

    assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("a", "a-value", "b", "b-value"));
  }
}
