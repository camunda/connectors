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
package io.camunda.connector.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResponseParserTest {

  private static final String ARRAY_NAME = "array_name";
  private static final String INNER_OBJECT_NAME = "object_name";
  private static final String OBJECT_VALUE_KEY = "object_value_key";
  private static final String INNER_OBJECT_VALUE_KEY = "inner_object_value_key";

  private static final String ARRAY_VALUE = "value in auth array response";
  private static final String OBJECT_VALUE = "object value";
  private static final String INNER_OBJECT_VALUE = "inner object value";

  private static final String KEY_NAME = "keyName";
  private static final int NUMBER_OF_ELEMENT_IN_ARRAY = 0;

  private final Gson gson = new Gson();
  private String authResponse;

  @BeforeEach
  public void init() {
    JsonObject jsonObject = new JsonObject();
    JsonArray array = new JsonArray();
    array.add(ARRAY_VALUE);
    jsonObject.add(ARRAY_NAME, array);

    jsonObject.addProperty(OBJECT_VALUE_KEY, OBJECT_VALUE);

    JsonObject innerObject = new JsonObject();
    innerObject.addProperty(INNER_OBJECT_VALUE_KEY, INNER_OBJECT_VALUE);

    jsonObject.add(INNER_OBJECT_NAME, innerObject);

    authResponse = gson.toJson(jsonObject);
  }

  @Test
  public void extractPropertiesFromBody_shouldReturnValueFromArray() {
    // given
    Map<String, String> prop = new HashMap<>();
    prop.put(
        KEY_NAME,
        "asObject:" + ARRAY_NAME + ".asArray:" + NUMBER_OF_ELEMENT_IN_ARRAY + ".asString");
    // when
    Map<String, String> stringStringMap =
        ResponseParser.extractPropertiesFromBody(prop, authResponse, gson);
    // then
    assertThat(stringStringMap.get(KEY_NAME)).isEqualTo(ARRAY_VALUE);
  }

  @Test
  public void extractPropertiesFromBody_shouldReturnValueFromObject() {
    // given
    Map<String, String> prop = new HashMap<>();
    prop.put(KEY_NAME, "asObject:" + OBJECT_VALUE_KEY + ".asString");
    // when
    Map<String, String> stringStringMap =
        ResponseParser.extractPropertiesFromBody(prop, authResponse, gson);
    // then
    assertThat(stringStringMap.get(KEY_NAME)).isEqualTo(OBJECT_VALUE);
  }

  @Test
  public void extractPropertiesFromBody_shouldReturnValueFromInnerObject() {
    // given
    Map<String, String> prop = new HashMap<>();
    prop.put(
        KEY_NAME,
        "asObject:" + INNER_OBJECT_NAME + ".asObject:" + INNER_OBJECT_VALUE_KEY + ".asString");
    // when
    Map<String, String> stringStringMap =
        ResponseParser.extractPropertiesFromBody(prop, authResponse, gson);
    // then
    assertThat(stringStringMap.get(KEY_NAME)).isEqualTo(INNER_OBJECT_VALUE);
  }
}
