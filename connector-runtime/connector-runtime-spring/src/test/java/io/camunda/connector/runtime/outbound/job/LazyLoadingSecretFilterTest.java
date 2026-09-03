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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyLoadingSecretFilterTest {

  @Test
  void isAllowed_withAllowList_permitsListedSecret() {
    var filter =
        new LazyLoadingSecretFilter(
            () -> List.of(secret("MY_SECRET", "foo"), secret("OTHER_SECRET", "bar")));

    assertTrue(filter.isAllowed(secret("MY_SECRET", "foo")));
    assertTrue(filter.isAllowed(secret("MY_SECRET", "foo", "nested")));
    assertTrue(filter.isAllowed(secret("OTHER_SECRET", "bar")));
  }

  @Test
  void isAllowed_withAllowList_deniesUnlistedSecret() {
    var filter = new LazyLoadingSecretFilter(() -> List.of(secret("MY_SECRET", "foo", "bar")));

    assertFalse(filter.isAllowed(secret("UNLISTED_SECRET", "foo", "bar")));
    assertFalse(filter.isAllowed(secret("MY_SECRET", "foo")));
    assertFalse(filter.isAllowed(secret("MY_SECRET", "baz")));
  }

  @Test
  void isAllowed_withNullSupplierResult_allowsAll() {
    var filter = new LazyLoadingSecretFilter(() -> null);

    assertTrue(filter.isAllowed(secret("ANY_SECRET")));
    assertTrue(filter.isAllowed(secret("ANOTHER_SECRET")));
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

    filter.isAllowed(secret("SECRET"));
    filter.isAllowed(secret("SECRET"));
    filter.isAllowed(secret("OTHER"));

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

    filter.isAllowed(secret("ANY"));
    filter.isAllowed(secret("ANY"));

    assertTrue(callCount.get() == 1, "Supplier must be called exactly once even when null");
  }

  @Test
  void isAllowed_supplierThrows_failureCachedAndSupplierNotReinvoked() {
    var callCount = new AtomicInteger(0);
    var filter =
        new LazyLoadingSecretFilter(
            () -> {
              callCount.incrementAndGet();
              throw new IllegalArgumentException("lookup failed");
            });

    assertThrows(IllegalArgumentException.class, () -> filter.isAllowed(secret("ANY")));
    assertThrows(IllegalArgumentException.class, () -> filter.isAllowed(secret("ANY")));

    assertTrue(callCount.get() == 1, "Supplier must not be re-invoked after a cached failure");
  }

  @Test
  void isAllowed_supplierThrowsError_doesNotFailOpenOnSubsequentCalls() {
    var callCount = new AtomicInteger(0);
    var filter =
        new LazyLoadingSecretFilter(
            () -> {
              callCount.incrementAndGet();
              throw new NoClassDefFoundError("some.missing.Class");
            });

    assertThrows(NoClassDefFoundError.class, () -> filter.isAllowed(secret("UNDECLARED_SECRET")));
    assertThrows(
        NoClassDefFoundError.class, () -> filter.isAllowed(secret("ANOTHER_UNDECLARED_SECRET")));

    assertTrue(callCount.get() == 1, "Supplier must not be re-invoked after a cached failure");
  }

  private static Secret secret(String name, String... fieldPath) {
    return new Secret(name, List.of(fieldPath));
  }
}
