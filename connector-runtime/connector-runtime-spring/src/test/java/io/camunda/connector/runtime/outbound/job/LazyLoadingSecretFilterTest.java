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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyLoadingSecretFilterTest {

  @Test
  void isAllowed_withAllowList_permitsListedSecret() {
    var filter = new LazyLoadingSecretFilter(() -> List.of("MY_SECRET", "OTHER_SECRET"));

    assertTrue(filter.isAllowed("MY_SECRET"));
    assertTrue(filter.isAllowed("OTHER_SECRET"));
  }

  @Test
  void isAllowed_withAllowList_deniesUnlistedSecret() {
    var filter = new LazyLoadingSecretFilter(() -> List.of("MY_SECRET"));

    assertFalse(filter.isAllowed("UNLISTED_SECRET"));
  }

  @Test
  void isAllowed_withNullSupplierResult_allowsAll() {
    var filter = new LazyLoadingSecretFilter(() -> null);

    assertTrue(filter.isAllowed("ANY_SECRET"));
    assertTrue(filter.isAllowed("ANOTHER_SECRET"));
  }

  @Test
  void isAllowed_supplierCalledExactlyOnce() {
    var callCount = new AtomicInteger(0);
    var filter =
        new LazyLoadingSecretFilter(
            () -> {
              callCount.incrementAndGet();
              return List.of("SECRET");
            });

    filter.isAllowed("SECRET");
    filter.isAllowed("SECRET");
    filter.isAllowed("OTHER");

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

    filter.isAllowed("ANY");
    filter.isAllowed("ANY");

    assertTrue(callCount.get() == 1, "Supplier must be called exactly once even when null");
  }
}
