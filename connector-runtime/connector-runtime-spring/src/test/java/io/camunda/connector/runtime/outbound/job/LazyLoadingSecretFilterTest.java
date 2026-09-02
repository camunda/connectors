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
package io.camunda.connector.runtime.outbound.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyLoadingSecretFilterTest {

  private static Secret secret(String name) {
    return new Secret(name, List.of());
  }

  private static Secret query(String name) {
    return new Secret(name, List.of("field"));
  }

  private static Secret at(String name, String... fieldPath) {
    return new Secret(name, List.of(fieldPath));
  }

  @Test
  void isAllowed_withAllowList_permitsListedSecret() {
    var filter =
        new LazyLoadingSecretFilter(() -> List.of(secret("MY_SECRET"), secret("OTHER_SECRET")));

    assertTrue(filter.isAllowed(query("MY_SECRET")));
    assertTrue(filter.isAllowed(query("OTHER_SECRET")));
  }

  @Test
  void isAllowed_withAllowList_deniesUnlistedSecret() {
    var filter = new LazyLoadingSecretFilter(() -> List.of(secret("MY_SECRET")));

    assertFalse(filter.isAllowed(query("UNLISTED_SECRET")));
  }

  @Test
  void isAllowed_permitsRuntimePathNestedDeeperThanDeclaredPath() {
    // The declared path is derived statically from the zeebe:input target (e.g. "foo"), but a FEEL
    // context/object expression can put the resolved value under a subpath of that at runtime (e.g.
    // "foo.bar"). The declared path only has to be a prefix of the runtime path, not an exact
    // match,
    // or every input that expands into a nested object would spuriously get its secrets rejected.
    var filter = new LazyLoadingSecretFilter(() -> List.of(at("TOKEN", "foo")));

    assertTrue(filter.isAllowed(at("TOKEN", "foo", "bar")));
  }

  @Test
  void isAllowed_permitsExactRuntimePathMatch() {
    var filter = new LazyLoadingSecretFilter(() -> List.of(at("TOKEN", "foo", "bar")));

    assertTrue(filter.isAllowed(at("TOKEN", "foo", "bar")));
  }

  @Test
  void isAllowed_deniesRuntimePathShallowerThanDeclaredPath() {
    // The reverse of the nesting case above: a secret statically declared at the deeper path
    // "foo.bar" must not match a runtime lookup at the shallower "foo" — the declared path can only
    // be a prefix of the runtime one, never the other way around.
    var filter = new LazyLoadingSecretFilter(() -> List.of(at("TOKEN", "foo", "bar")));

    assertFalse(filter.isAllowed(at("TOKEN", "foo")));
  }

  @Test
  void isAllowed_deniesRuntimePathOnASiblingBranch() {
    var filter = new LazyLoadingSecretFilter(() -> List.of(at("TOKEN", "foo")));

    assertFalse(filter.isAllowed(at("TOKEN", "baz")));
    assertFalse(filter.isAllowed(at("TOKEN", "baz", "bar")));
  }

  @Test
  void isAllowed_withNullSupplierResult_allowsAll() {
    var filter = new LazyLoadingSecretFilter(() -> null);

    assertTrue(filter.isAllowed(query("ANY_SECRET")));
    assertTrue(filter.isAllowed(query("ANOTHER_SECRET")));
  }

  @Test
  void isAllowed_supplierCalledExactlyOnce() {
    var callCount = new AtomicInteger(0);
    var filter =
        new LazyLoadingSecretFilter(
            () -> {
              callCount.incrementAndGet();
              return List.of(secret("SECRET"));
            });

    filter.isAllowed(query("SECRET"));
    filter.isAllowed(query("SECRET"));
    filter.isAllowed(query("OTHER"));

    assertTrue(callCount.get() == 1, "Supplier must be called exactly once");
  }

  @Test
  void isAllowed_nullSupplierResultCached_supplierNotReinvoked() {
    var callCount = new AtomicInteger(0);
    var filter =
        new LazyLoadingSecretFilter(
            () -> {
              callCount.incrementAndGet();
              return null;
            });

    filter.isAllowed(query("ANY"));
    filter.isAllowed(query("ANY"));

    assertTrue(callCount.get() == 1, "Supplier must be called exactly once even when null");
  }
}
