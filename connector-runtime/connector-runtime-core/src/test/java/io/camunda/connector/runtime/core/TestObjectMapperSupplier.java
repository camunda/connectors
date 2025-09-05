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
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;

public class TestObjectMapperSupplier {

  public static final ObjectMapper INSTANCE = getInstance();

  public static ObjectMapper getInstance() {
    var copy = ConnectorsObjectMapperSupplier.getCopy();
    var documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory, functionExecutor, DocumentModuleSettings.create());
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }
}
