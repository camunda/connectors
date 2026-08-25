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
package io.camunda.connector.runtime.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import java.util.Map;

/**
 * Builds the mapper that binds the values FEEL evaluations return, as distinct from the mapper that
 * binds a connector's properties.
 *
 * <p>The two differ in one way: this one registers neither the FEEL module nor the secret-reference
 * module. An evaluation result may contain arbitrary process data — a webhook payload, a correlated
 * variable — so no string in it may be treated as expression source. Nothing has to detect that a
 * value came from an evaluation, because nothing that would act on it is registered.
 *
 * <p>The document modules are registered, so a document reference in a result still materialises,
 * which a blank mapper would not.
 *
 * <p>Pass the result to {@code JacksonModuleFeelFunction}. A wiring that does not falls back to a
 * blank mapper: plain data still binds, documents in results do not.
 */
public final class FeelEvaluationResultMapper {

  private FeelEvaluationResultMapper() {}

  /** For a wiring with a single document factory. */
  public static ObjectMapper create(DocumentFactory documentFactory) {
    ObjectMapper mapper = base();
    return mapper.registerModules(
        new JacksonModuleDocumentDeserializer(
            documentFactory,
            new DefaultIntrinsicFunctionExecutor(mapper),
            DocumentModuleSettings.create()),
        new JacksonModuleDocumentSerializer());
  }

  /** For a wiring whose document factory depends on the physical tenant. */
  public static ObjectMapper create(Map<String, DocumentFactory> documentFactoriesByTenantId) {
    ObjectMapper mapper = base();
    return mapper.registerModules(
        new JacksonModuleDocumentDeserializer(
            documentFactoriesByTenantId,
            new DefaultIntrinsicFunctionExecutor(mapper),
            DocumentModuleSettings.create()),
        new JacksonModuleDocumentSerializer());
  }

  /**
   * For a wiring with no document factory. A document reference in a result is not materialised.
   */
  public static ObjectMapper create() {
    return base().registerModules(new JacksonModuleDocumentSerializer());
  }

  private static ObjectMapper base() {
    return ConnectorsObjectMapperSupplier.getCopy();
  }
}
