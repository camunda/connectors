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
package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.document.factory.DocumentFactory;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultInboundConnectorContextFactoryTest {

  @Mock private ObjectMapper objectMapper;
  @Mock private InboundCorrelationHandler correlationHandler;
  @Mock private SecretProviderAggregator secretProviderAggregator;
  @Mock private ValidationProvider validationProvider;
  @Mock private ProcessInstanceClient operateClientAdapter;
  @Mock private Consumer<Throwable> cancellationCallback;
  @Mock private ValidInboundConnectorDetails newConnector;
  @Mock private DocumentFactory documentFactory;
  private DefaultInboundConnectorContextFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new DefaultInboundConnectorContextFactory(
            objectMapper,
            correlationHandler,
            secretProviderAggregator,
            validationProvider,
            operateClientAdapter,
            documentFactory);
  }

  @Test
  void shouldCreateInboundConnectorContext() {
    InboundConnectorContext result =
        factory.createContext(
            newConnector,
            cancellationCallback,
            ExecutableWithInboundContext.class,
            EvictingQueue.create(10));

    assertThat(result).isExactlyInstanceOf(InboundConnectorContextImpl.class);
  }

  @Test
  void shouldCreateInboundConnectorContextWhenParameterizedTypeIsEmpty() {
    InboundConnectorContext result =
        factory.createContext(
            newConnector,
            cancellationCallback,
            ExecutableWithEmptyParameterizedType.class,
            EvictingQueue.create(10));

    assertThat(result).isExactlyInstanceOf(InboundConnectorContextImpl.class);
  }

  @Test
  void shouldCreateInboundIntermediateConnectorContext() {

    InboundConnectorContext result =
        factory.createContext(
            newConnector,
            cancellationCallback,
            ExecutableWithIntermediate.class,
            EvictingQueue.create(10));

    assertThat(result).isExactlyInstanceOf(InboundIntermediateConnectorContextImpl.class);
  }

  static class ExecutableWithInboundContext
      implements InboundConnectorExecutable<InboundConnectorContext> {
    @Override
    public void activate(final InboundConnectorContext context) throws Exception {}

    @Override
    public void deactivate() throws Exception {}
  }

  static class ExecutableWithEmptyParameterizedType
      implements InboundConnectorExecutable<InboundConnectorContext> {

    @Override
    public void activate(final InboundConnectorContext context) throws Exception {}

    @Override
    public void deactivate() throws Exception {}
  }

  static class ExecutableWithIntermediate
      implements InboundConnectorExecutable<InboundIntermediateConnectorContext> {

    @Override
    public void activate(final InboundIntermediateConnectorContext context) throws Exception {}

    @Override
    public void deactivate() throws Exception {}
  }
}
