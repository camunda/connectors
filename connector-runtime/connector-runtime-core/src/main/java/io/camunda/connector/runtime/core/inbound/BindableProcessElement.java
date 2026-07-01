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

import io.camunda.connector.api.inbound.ProcessElement;
import java.util.Map;

/**
 * A {@link ProcessElement} that can bind its own raw properties to a typed object. It delegates all
 * element metadata to the wrapped element and adds element-scoped property binding via a runtime
 * {@link PropertyBinder} (typically the connector context's secret-replacement + FEEL pipeline).
 *
 * <p>The runtime attaches this to the activated element of a {@link
 * io.camunda.connector.api.inbound.CorrelationResult.Success} so callers can use {@code
 * success.bindProperties(SomeClass.class)} without handling raw property maps.
 */
public record BindableProcessElement(ProcessElement delegate, PropertyBinder binder)
    implements ProcessElement {

  /** Binds a raw (FEEL-unevaluated, secret-unresolved) element property map to a typed object. */
  @FunctionalInterface
  public interface PropertyBinder {
    <T> T bind(Map<String, String> rawProperties, Class<T> type);
  }

  @Override
  public <T> T bindProperties(Class<T> cls) {
    return binder.bind(delegate.properties(), cls);
  }

  @Override
  public Map<String, String> properties() {
    return delegate.properties();
  }

  @Override
  public String bpmnProcessId() {
    return delegate.bpmnProcessId();
  }

  @Override
  public String processName() {
    return delegate.processName();
  }

  @Override
  public int version() {
    return delegate.version();
  }

  @Override
  public long processDefinitionKey() {
    return delegate.processDefinitionKey();
  }

  @Override
  public String elementId() {
    return delegate.elementId();
  }

  @Override
  public String elementName() {
    return delegate.elementName();
  }

  @Override
  public String elementType() {
    return delegate.elementType();
  }

  @Override
  public String tenantId() {
    return delegate.tenantId();
  }
}
