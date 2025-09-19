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
package io.camunda.connector.runtime.test.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.connector.test.ConnectorContextTestUtil;
import io.camunda.connector.test.MapSecretProvider;
import java.util.HashMap;
import java.util.Map;

/** Test helper class for creating a {@link OutboundConnectorContext} with a fluent API. */
public class OutboundConnectorContextBuilder {

  protected final Map<String, String> secrets = new HashMap<>();
  protected final Map<String, String> headers = new HashMap<>();
  protected SecretProvider secretProvider = new MapSecretProvider(secrets);
  protected ValidationProvider validationProvider =
      ValidationUtil.discoverDefaultValidationProviderImplementation();
  protected Map<String, Object> variables;
  protected DocumentFactory documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private ObjectMapper objectMapper = createObjectMapper();

  private ObjectMapper createObjectMapper() {
    var copy = ConnectorsObjectMapperSupplier.getCopy();
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory, functionExecutor, DocumentModuleSettings.create());
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }

  private ObjectMapper createObjectMapper(DocumentFactory documentFactory) {
    var copy = ConnectorsObjectMapperSupplier.getCopy();
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory, functionExecutor, DocumentModuleSettings.create());
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }

  /**
   * @return a new instance of the {@link OutboundConnectorContextBuilder}
   */
  public static OutboundConnectorContextBuilder create() {
    return new OutboundConnectorContextBuilder();
  }

  private void assertNoVariables() {
    if (this.variables != null) {
      throw new IllegalStateException("Variables already set");
    }
  }

  /**
   * Provides the variables as a JSON string.
   *
   * @param variablesAsJSON - the variables as JSON
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder variables(String variablesAsJSON) {
    this.assertNoVariables();
    try {
      this.variables = objectMapper.readValue(variablesAsJSON, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON: " + variablesAsJSON, e);
    }
    return this;
  }

  /**
   * Provides the variables as a map.
   *
   * @param variables - the variables as map
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder variables(Map<String, ?> variables) {
    this.assertNoVariables();
    this.variables = (Map<String, Object>) ConnectorContextTestUtil.replaceImmutableMaps(variables);
    return this;
  }

  /**
   * Provides multiple variables as object. The variables will then be converted to an intermediate
   * Map representation.
   *
   * @param variables - new variables
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder variables(Object variables) {
    this.assertNoVariables();
    this.variables = objectMapper.convertValue(variables, new TypeReference<>() {});
    return this;
  }

  /**
   * Provides the variable value for the given name. Nested variables can be provided like
   * "foo.bar.baz".
   *
   * @param key - property name
   * @param value - property value
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder variable(String key, String value) {
    if (variables == null) {
      variables = new HashMap<>();
    }
    ConnectorContextTestUtil.addVariable(key, value, variables);
    return this;
  }

  /**
   * Provides the custom header value with given key/value pair
   *
   * @param key - custom header key
   * @param value - custom header key
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder header(String key, String value) {
    headers.put(key, value);
    return this;
  }

  /**
   * Provides the secret's value for the given name.
   *
   * @param name - the secret's name, e.g. MY_SECRET when referred to as "secrets.MY_SECRET"
   * @param value - the secret's value
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder secret(String name, String value) {
    secrets.put(name, value);
    return this;
  }

  /**
   * Provides the secret values via the defined {@link SecretProvider}.
   *
   * @param secretProvider - provider for secret values, given a secret name
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder secrets(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
    return this;
  }

  public OutboundConnectorContextBuilder validation(ValidationProvider validationProvider) {
    this.validationProvider = validationProvider;
    return this;
  }

  /**
   * Sets a custom {@link ObjectMapper} that is used to serialize and deserialize the variables. If
   * not provided, default mapper will be used.
   *
   * @param objectMapper - custom object mapper
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder objectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  public OutboundConnectorContextBuilder documentFactory(DocumentFactory documentFactory) {
    this.objectMapper = createObjectMapper(documentFactory);
    return this;
  }

  /**
   * @return the {@link OutboundConnectorContext} including all previously defined properties
   */
  public TestConnectorContext build() {
    return new TestConnectorContext(secretProvider, validationProvider);
  }

  public class TestConnectorContext extends AbstractConnectorContext
      implements OutboundConnectorContext {

    private final String variablesWithSecrets;

    private final TestJobContext jobContext;

    protected TestConnectorContext(
        SecretProvider secretProvider, ValidationProvider validationProvider) {
      super(secretProvider, validationProvider);
      try {
        var asString = objectMapper.writeValueAsString(variables);
        variablesWithSecrets = getSecretHandler().replaceSecrets(asString, null);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
      this.jobContext = new TestJobContext(() -> headers, () -> variablesWithSecrets);
    }

    @Override
    public JobContext getJobContext() {
      return jobContext;
    }

    @Override
    public <T> T bindVariables(Class<T> cls) {
      try {
        var mappedObject = objectMapper.readValue(variablesWithSecrets, cls);
        if (validationProvider != null) {
          getValidationProvider().validate(mappedObject);
        }
        return mappedObject;
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Document resolve(DocumentReference reference) {
      return documentFactory.resolve(reference);
    }

    @Override
    public Document create(DocumentCreationRequest request) {
      return documentFactory.create(request);
    }
  }
}
