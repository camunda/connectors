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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList.Call;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyLoadingIntrinsicFunctionAllowListTest {

  @Test
  void isAllowed_permitsADeclaredCall() {
    var allowList =
        new LazyLoadingIntrinsicFunctionAllowList(
            () -> List.of(new AllowedIntrinsicFunction("base64", List.of("body"))));

    assertTrue(allowList.isAllowed(new Call("base64", List.of("body"))));
  }

  @Test
  void isAllowed_deniesAnUndeclaredCall() {
    var allowList =
        new LazyLoadingIntrinsicFunctionAllowList(
            () -> List.of(new AllowedIntrinsicFunction("base64", List.of("body"))));

    assertFalse(allowList.isAllowed(new Call("createLink", List.of("body"))));
  }

  @Test
  void isAllowed_supplierCalledExactlyOnce() {
    var callCount = new AtomicInteger(0);
    var allowList =
        new LazyLoadingIntrinsicFunctionAllowList(
            () -> {
              callCount.incrementAndGet();
              return List.of(new AllowedIntrinsicFunction("base64", List.of("body")));
            });

    allowList.isAllowed(new Call("base64", List.of("body")));
    allowList.isAllowed(new Call("base64", List.of("body")));
    allowList.isAllowed(new Call("createLink", List.of("other")));

    assertTrue(callCount.get() == 1, "Supplier must be called exactly once");
  }

  @Test
  void isAllowed_supplierNeverCalledUntilFirstIsAllowedCall() {
    // The whole point of this class: constructing it (mirroring what create() now does) must not
    // by itself trigger the BPMN-model fetch the supplier performs.
    var callCount = new AtomicInteger(0);
    new LazyLoadingIntrinsicFunctionAllowList(
        () -> {
          callCount.incrementAndGet();
          return List.of();
        });

    assertTrue(callCount.get() == 0, "Supplier must not be called before isAllowed is invoked");
  }

  @Test
  void isAllowed_supplierFailureCached_supplierNotReinvoked() {
    var callCount = new AtomicInteger(0);
    var failure = new IllegalStateException("BPMN fetch failed");
    var allowList =
        new LazyLoadingIntrinsicFunctionAllowList(
            () -> {
              callCount.incrementAndGet();
              throw failure;
            });

    var firstThrown =
        assertThrows(
            IllegalStateException.class,
            () -> allowList.isAllowed(new Call("base64", List.of("body"))));
    var secondThrown =
        assertThrows(
            IllegalStateException.class,
            () -> allowList.isAllowed(new Call("base64", List.of("body"))));

    assertTrue(callCount.get() == 1, "Supplier must be called exactly once even on failure");
    assertSame(failure, firstThrown);
    assertSame(failure, secondThrown);
  }
}
