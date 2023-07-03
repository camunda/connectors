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
package io.camunda.connector.example.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;

public class ExampleTestData {

  static final ObjectMapper objectMapper = new ObjectMapper();

  static String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static final String Object = toJson(new ExampleOutboundInput("FOO", null));
  public static final String ObjectWithSecret =
      toJson(new ExampleOutboundInput("secrets.value", null));

  public static final String ObjectAndListWithSecrets =
      toJson(new ExampleOutboundInput("FOO", Arrays.asList("foo", "secrets.value")));

  public static final String SingleKeyObjectWithSecret =
      toJson(Collections.singletonMap("foo", "secrets.value"));

  public static final String SingleKeyObjectWithSecretInParentheses =
      toJson(Collections.singletonMap("foo", "{{secrets.value}}"));
}
