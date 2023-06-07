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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.Constants;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.runtime.core.feel.FeelParserWrapper;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext {

  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);
  private static final Set<String> reservedKeys =
      Set.of(
          Constants.ACTIVATION_CONDITION_KEYWORD,
          Constants.LEGACY_VARIABLE_MAPPING_KEYWORD,
          Constants.INBOUND_TYPE_KEYWORD,
          Constants.RESULT_VARIABLE_KEYWORD,
          Constants.RESULT_EXPRESSION_KEYWORD,
          Constants.ERROR_EXPRESSION_KEYWORD,
          Constants.CORRELATION_KEY_EXPRESSION_KEYWORD);

  private final InboundConnectorProperties properties;
  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;

  private Health health = Health.unknown();

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

    Map<String, Object> result =
        properties.getPropertiesAsObjectMap().entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        isReservedKey(entry.getKey())
                            ? entry.getValue()
                            : FeelParserWrapper.parseIfIsFeelExpressionOrGetOriginal(
                                entry.getValue())));

    return objectMapper.convertValue(result, cls);
  }

  private static boolean isReservedKey(final String value) {
    return reservedKeys.contains(value);
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
  public void reportHealth(Health health) {
    this.health = health;
  }

  public Health getHealth() {
    return health;
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
