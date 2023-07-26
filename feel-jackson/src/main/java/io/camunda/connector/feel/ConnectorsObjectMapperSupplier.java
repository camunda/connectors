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
package io.camunda.connector.feel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.connector.feel.jackson.FeelAnnotationIntrospector;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;

/** Default ObjectMapper supplier to be used by the connector runtime. */
public class ConnectorsObjectMapperSupplier {

  private ConnectorsObjectMapperSupplier() {}

  public static ObjectMapper DEFAULT_MAPPER =
      new ObjectMapper()
          .setAnnotationIntrospector(new FeelAnnotationIntrospector())
          .registerModule(new JacksonModuleFeelFunction())
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          // .registerModule(DefaultScalaModule$.MODULE$)
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public static ObjectMapper getCopy() {
    return DEFAULT_MAPPER.copy();
  }
}
