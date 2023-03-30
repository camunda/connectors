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
package io.camunda.connector.runtime.util.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.runtime.util.inbound.correlation.InboundCorrelationHandler;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext {
  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);

  private final InboundConnectorProperties properties;
  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      InboundConnectorProperties properties,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback) {
    super(secretProvider);
    this.correlationHandler = correlationHandler;
    this.properties = properties;
    this.cancellationCallback = cancellationCallback;
    this.objectMapper =
        new ObjectMapper()
            .registerModule(DefaultScalaModule$.MODULE$)
            .registerModule(new JavaTimeModule())
            // deserialize unknown types as empty objects
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      InboundConnectorProperties properties,
      InboundCorrelationHandler correlationHandler,
      ObjectMapper objectMapper,
      Consumer<Throwable> cancellationCallback) {
    super(secretProvider);
    this.correlationHandler = correlationHandler;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
  }

  @Override
  public InboundConnectorResult<?> correlate(Object variables) {
    return correlationHandler.correlate(properties, variables);
  }

  @Override
  public void cancel(Throwable exception) {
    try {
      cancellationCallback.accept(exception);
    } catch (Throwable e) {
      LOG.error("Failed to deliver the cancellation signal to the runtime", e);
    }
  }

  @Override
  public InboundConnectorProperties getProperties() {
    return properties;
  }

  @Override
  public <T> T getPropertiesAsType(Class<T> cls) {
    return objectMapper.convertValue(properties.getProperties(), cls);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InboundConnectorContextImpl that = (InboundConnectorContextImpl) o;
    return Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(properties);
  }

  @Override
  public String toString() {
    return "InboundConnectorContextImpl{" + "properties=" + properties + '}';
  }
}
