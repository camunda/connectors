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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
import io.camunda.connector.runtime.outbound.job.ConfigurableIntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListMode;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionIntrinsicFunctionAllowListCache;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurableIntrinsicFunctionAllowListFactoryTest {

  private final IntrinsicFunctionAllowListContext context =
      new IntrinsicFunctionAllowListContext(1L, "elementA", Instant.now().plusSeconds(30));

  @Test
  void disabledModeRefusesEverythingWithoutConsultingTheCache() {
    // DISABLED must not mean allowAll(): it's a kill switch for a topology that cannot reach the
    // BPMN-fetch endpoint at all, not a way back to unconditional dispatch (see class javadoc).
    var allowListCache = mock(ProcessDefinitionIntrinsicFunctionAllowListCache.class);
    var factory =
        new ConfigurableIntrinsicFunctionAllowListFactory(
            IntrinsicFunctionAllowListMode.DISABLED, allowListCache);

    var allowList = factory.create(context);

    assertThat(allowList.isAllowed(new IntrinsicFunctionAllowList.Call("anything", List.of("x"))))
        .isFalse();
    verifyNoInteractions(allowListCache);
  }

  @Test
  void enabledModeConsultsTheCache() {
    var allowListCache = mock(ProcessDefinitionIntrinsicFunctionAllowListCache.class);
    when(allowListCache.getAllowedFunctions(context))
        .thenReturn(List.of(new AllowedIntrinsicFunction("base64", List.of("b64content"))));
    var factory =
        new ConfigurableIntrinsicFunctionAllowListFactory(
            IntrinsicFunctionAllowListMode.ENABLED, allowListCache);

    var allowList = factory.create(context);

    assertThat(
            allowList.isAllowed(
                new IntrinsicFunctionAllowList.Call("base64", List.of("b64content"))))
        .isTrue();
    assertThat(
            allowList.isAllowed(new IntrinsicFunctionAllowList.Call("createLink", List.of("body"))))
        .isFalse();
  }

  @Test
  void enabledModeFailsClosedWhenTheCacheThrows() {
    var allowListCache = mock(ProcessDefinitionIntrinsicFunctionAllowListCache.class);
    when(allowListCache.getAllowedFunctions(context))
        .thenThrow(new RuntimeException("BPMN fetch failed"));

    assertThatThrownBy(
            () ->
                new ConfigurableIntrinsicFunctionAllowListFactory(
                        IntrinsicFunctionAllowListMode.ENABLED, allowListCache)
                    .create(context))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("BPMN fetch failed");
  }
}
