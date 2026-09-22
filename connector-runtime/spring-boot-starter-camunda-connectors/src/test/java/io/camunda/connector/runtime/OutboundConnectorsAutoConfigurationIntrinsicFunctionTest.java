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
package io.camunda.connector.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.camunda.document.factory.DocumentFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the trust-boundary split {@link OutboundConnectorsAutoConfiguration}
 * introduces (security-testing-findings#275): the general-purpose {@code objectMapper} bean must
 * never dispatch a {@code camunda.function.type} call, while {@code outboundConnectorObjectMapper}
 * — used only to bind a job-variable tree {@code JobHandlerContext}'s allow-list gate has already
 * checked — must.
 */
class OutboundConnectorsAutoConfigurationIntrinsicFunctionTest {

  private static final String DISCRIMINATOR_SHAPED_JSON =
      """
      {"payload": {"camunda.function.type":"base64","params":["test"]}}
      """;

  private final OutboundConnectorsAutoConfiguration configuration =
      new OutboundConnectorsAutoConfiguration();

  @Test
  void generalPurposeMapper_refusesToDispatchAnIntrinsicFunction() throws Exception {
    var mapper = configuration.objectMapper(mock(DocumentFactory.class));

    assertThatThrownBy(() -> mapper.readValue(DISCRIMINATOR_SHAPED_JSON, Map.class))
        .isInstanceOf(JsonMappingException.class)
        .hasStackTraceContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void outboundConnectorMapper_dispatchesADeclaredIntrinsicFunction() throws Exception {
    var mapper = configuration.outboundConnectorObjectMapper(mock(DocumentFactory.class));

    @SuppressWarnings("unchecked")
    var bound = (Map<String, Object>) mapper.readValue(DISCRIMINATOR_SHAPED_JSON, Map.class);

    // base64("test") -- proof the function actually executed during binding, not just that the
    // discriminator survived as inert data.
    assertThat(bound).containsEntry("payload", "dGVzdA==");
  }

  @Test
  void theTwoMappersAreDistinctInstances() {
    var documentFactory = mock(DocumentFactory.class);

    assertThat(configuration.objectMapper(documentFactory))
        .isNotSameAs(configuration.outboundConnectorObjectMapper(documentFactory));
  }
}
