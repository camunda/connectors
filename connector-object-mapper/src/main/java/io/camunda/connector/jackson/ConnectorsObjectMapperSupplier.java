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
package io.camunda.connector.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.intrinsic.IntrinsicFunctionExecutor;

/** Default ObjectMapper supplier to be used by OOTB connectors and the Connector runtime. */
public class ConnectorsObjectMapperSupplier {

  private static final ObjectMapper DEFAULT_MAPPER =
      JsonMapper.builder()
          .addModules(
              new JacksonModuleFeelFunction(),
              new Jdk8Module(),
              new JavaTimeModule(),
              new JacksonModuleDocumentSerializer())
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
          .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
          .build();

  private ConnectorsObjectMapperSupplier() {}

  public static ObjectMapper getCopy() {
    return DEFAULT_MAPPER.copy();
  }

  public static ObjectMapper getCopy(DocumentFactory factory, DocumentModuleSettings settings) {
    final ObjectMapper copy = DEFAULT_MAPPER.copy();
    final IntrinsicFunctionExecutor functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);
    return copy.registerModule(
        new JacksonModuleDocumentDeserializer(factory, functionExecutor, settings));
  }
}
