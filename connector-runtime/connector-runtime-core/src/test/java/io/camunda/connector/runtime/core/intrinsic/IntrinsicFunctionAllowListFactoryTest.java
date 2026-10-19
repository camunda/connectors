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
package io.camunda.connector.runtime.core.intrinsic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList.Call;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks in that {@link IntrinsicFunctionAllowListFactory#disabled()} is fail-closed. It has no
 * production call site today (the one production caller, {@code SpringConnectorJobHandler}, always
 * builds a real per-tenant {@code ConfigurableIntrinsicFunctionAllowListFactory} — see
 * security-testing-findings#275's review discussion) — every existing call site is a test
 * exercising unrelated behavior. This test exists so that property holds even if a future caller
 * reaches for {@code disabled()} expecting a safe, neutral placeholder: it must never silently
 * become the "unconditional dispatch" fail-open a name like "disabled" could otherwise suggest.
 */
class IntrinsicFunctionAllowListFactoryTest {

  @Test
  void disabledRefusesAnyIntrinsicFunctionCall() {
    var factory = IntrinsicFunctionAllowListFactory.disabled();

    var allowList =
        factory.create(
            new IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext(
                1L, "elementA", Instant.now().plusSeconds(30)));

    assertThat(allowList.isAllowed(new Call("createLink", List.of("body")))).isFalse();
    assertThat(
            allowList.isAllowed(
                new Call("createGithubAppInstallationToken", List.of("authentication", "token"))))
        .isFalse();
  }

  @Test
  void disabledDoesNotVaryByContext() {
    var factory = IntrinsicFunctionAllowListFactory.disabled();

    var allowListA =
        factory.create(
            new IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext(
                1L, "elementA", Instant.now().plusSeconds(30)));
    var allowListB =
        factory.create(
            new IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext(
                2L, "elementB", Instant.now().plusSeconds(30)));

    assertThat(allowListA.isAllowed(new Call("base64", List.of("b64content")))).isFalse();
    assertThat(allowListB.isAllowed(new Call("base64", List.of("b64content")))).isFalse();
  }
}
