/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

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
