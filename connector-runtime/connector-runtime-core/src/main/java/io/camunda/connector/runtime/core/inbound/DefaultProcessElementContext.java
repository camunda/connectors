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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.ProcessElementContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.Map;

public class DefaultProcessElementContext extends AbstractConnectorContext
    implements ProcessElementContext {

  private final InboundConnectorElement connectorElement;

  private final Map<String, Object> properties;
  private final ObjectMapper objectMapper;

  private Map<String, Object> propertiesWithSecrets;

  public DefaultProcessElementContext(
      InboundConnectorElement connectorElement,
      ValidationProvider validationProvider,
      SecretProvider secretProvider,
      ObjectMapper objectMapper) {
    this(connectorElement, validationProvider, secretProvider, objectMapper, false);
  }

  /**
   * @param secretFilterEnabled when {@code true}, restricts secret resolution to the names this
   *     element's own deployed {@code zeebe:property} text declares (#7730).
   */
  public DefaultProcessElementContext(
      InboundConnectorElement connectorElement,
      ValidationProvider validationProvider,
      SecretProvider secretProvider,
      ObjectMapper objectMapper,
      boolean secretFilterEnabled) {
    super(secretProvider, secretFilter(connectorElement, secretFilterEnabled), validationProvider);
    this.connectorElement = connectorElement;
    this.objectMapper = objectMapper;
    this.properties =
        InboundPropertyHandler.readWrappedProperties(connectorElement.rawProperties());
  }

  /**
   * Scoped to this one element's {@code rawProperties} — the exact text {@link
   * #getPropertiesWithSecrets} filters — rather than to every element of the executable, matching
   * the per-call scoping #8538 settled on upstream after a union across siblings proved too wide.
   *
   * <p>This context is a second inbound resolution path, distinct from {@code
   * InboundConnectorContextImpl}: every successful {@code CorrelationResult} carries one as its
   * {@code activatedElement} — {@code ProcessInstanceCreated}, {@code MessagePublished} and {@code
   * MessageAlreadyCorrelated}, built at three sites in {@code InboundCorrelationHandler} — and its
   * public {@code getProperties()} and {@code bindProperties()} resolve secrets too. It is the
   * analogue of main's {@code BindableProcessElement}, which is why upstream filters that and this
   * filters here. (Later branches also expose it through {@code canActivate}; that entry point does
   * not exist here.)
   *
   * <p>Static because it feeds the {@code super(...)} call, before any field is assigned.
   */
  private static SecretFilter secretFilter(
      InboundConnectorElement connectorElement, boolean secretFilterEnabled) {
    if (!secretFilterEnabled) {
      return SecretFilter.allowAll();
    }
    return SecretFilter.allowOnly(
        connectorElement.rawProperties().values().stream()
            .flatMap(value -> SecretUtil.retrieveSecretKeysInInput(value).stream())
            .distinct()
            .toList());
  }

  @Override
  public ProcessElement getElement() {
    return connectorElement.element();
  }

  @Override
  public <T> T bindProperties(Class<T> cls) {
    var mappedObject = objectMapper.convertValue(getPropertiesWithSecrets(properties), cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  @Override
  public Map<String, Object> getProperties() {
    return getPropertiesWithSecrets(properties);
  }

  private Map<String, Object> getPropertiesWithSecrets(Map<String, Object> properties) {
    if (propertiesWithSecrets == null) {
      propertiesWithSecrets =
          InboundPropertyHandler.getPropertiesWithSecrets(
              getSecretHandler(), objectMapper, properties);
    }
    return propertiesWithSecrets;
  }
}
