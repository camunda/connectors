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
package io.camunda.connector.runtime.instances.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InstanceForwardingRouterTest {

  private static final TypeReference<String> RESPONSE_TYPE = new TypeReference<>() {};

  @Test
  void localRouter_shouldAlwaysUseLocalImplementation() {
    var router = new LocalInstanceForwardingRouter();
    var request = new MockHttpServletRequest("GET", "/inbound-instances");

    var result =
        router.forwardToInstancesAndReduceOrLocal(
            request, null, () -> "local response", RESPONSE_TYPE);

    assertThat(result).isEqualTo("local response");
  }

  @Test
  void forwardingRouter_shouldForward_whenRequestWasNotAlreadyForwarded() {
    var forwardingService = mock(InstanceForwardingService.class);
    var router = new ForwardingInstanceForwardingRouter(forwardingService);
    var request = new MockHttpServletRequest("GET", "/inbound-instances");
    var localCalled = new AtomicBoolean(false);
    when(forwardingService.forwardAndReduce(request, RESPONSE_TYPE))
        .thenReturn("forwarded response");

    var result =
        router.forwardToInstancesAndReduceOrLocal(
            request,
            null,
            () -> {
              localCalled.set(true);
              return "local response";
            },
            RESPONSE_TYPE);

    assertThat(result).isEqualTo("forwarded response");
    assertThat(localCalled).isFalse();
    verify(forwardingService).forwardAndReduce(request, RESPONSE_TYPE);
  }

  @Test
  void forwardingRouter_shouldUseLocalImplementation_whenRequestWasAlreadyForwarded() {
    var forwardingService = mock(InstanceForwardingService.class);
    var router = new ForwardingInstanceForwardingRouter(forwardingService);
    var request = new MockHttpServletRequest("GET", "/inbound-instances");

    var result =
        router.forwardToInstancesAndReduceOrLocal(
            request, "runtime-1", () -> "local response", RESPONSE_TYPE);

    assertThat(result).isEqualTo("local response");
    verify(forwardingService, never()).forwardAndReduce(request, RESPONSE_TYPE);
  }
}
