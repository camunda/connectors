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
package io.camunda.connector.http.client;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * ObjectMapper instance supplier for HTTP client operations. This is separate from the shared
 * object mapper instance we use in other parts of the codebase to avoid cyclic dependencies (e.g.
 * HttpClient can be invoked in a Jackson deserializer in an attempt to execute an intrinsic
 * function).
 */
public class HttpClientObjectMapperSupplier {

  private static final ObjectMapper DEFAULT_MAPPER =
      JsonMapper.builder()
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          // Jackson 3 flips this feature's default to enabled; disabled here to keep this
          // mapper's null-handling identical to its Jackson 2 behavior.
          .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
          .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
          .build();

  private HttpClientObjectMapperSupplier() {}

  public static ObjectMapper getCopy() {
    return DEFAULT_MAPPER.rebuild().build();
  }
}
