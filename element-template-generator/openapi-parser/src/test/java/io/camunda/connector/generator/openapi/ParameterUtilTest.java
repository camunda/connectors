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
package io.camunda.connector.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class ParameterUtilTest {

  @Nested
  class ParameterEscaping {

    @Test
    void pathParameter_containsDash_replacedWithUnderscore() {
      // given
      var schema = new Schema<>();
      schema.setType("string");

      var parameter = new Parameter();
      parameter.setName("my-path-parameter");
      parameter.setIn("path");
      parameter.setSchema(schema);

      // when
      var property = ParameterUtil.transformToProperty(parameter, null);

      // then
      assertThat(property.id()).isEqualTo("my_path_parameter");
    }

    @Test
    void queryParameter_containsDash_notReplacedWithUnderscore() {
      // given
      var schema = new Schema<>();
      schema.setType("string");

      var parameter = new Parameter();
      parameter.setName("my-query-parameter");
      parameter.setIn("query");
      parameter.setSchema(schema);

      // when
      var property = ParameterUtil.transformToProperty(parameter, null);

      // then
      assertThat(property.id()).isEqualTo("my-query-parameter");
    }

    @Test
    void headerParameter_containsDash_notReplacedWithUnderscore() {
      // given
      var schema = new Schema<>();
      schema.setType("string");

      var parameter = new Parameter();
      parameter.setName("my-query-parameter");
      parameter.setIn("header");
      parameter.setSchema(schema);

      // when
      var property = ParameterUtil.transformToProperty(parameter, null);

      // then
      assertThat(property.id()).isEqualTo("my-query-parameter");
    }
  }

  @Nested
  class PropertyExamples {

    @Test
    void enumArrayExample_shouldContainAllValues() throws JSONException {
      // given
      var itemsSchema = new Schema<>();
      itemsSchema.setType("string");
      itemsSchema.setEnum(List.of("foo", "bar"));
      var schema = new Schema<>();
      schema.setType("array");
      schema.setItems(itemsSchema);

      var parameter = new Parameter();
      parameter.setSchema(schema);
      parameter.setName("myParameter");
      parameter.setIn("query");

      // when
      var property = ParameterUtil.transformToProperty(parameter, null);

      // then
      JSONAssert.assertEquals("[\"foo\", \"bar\"]", property.example(), true);
    }

    @Test
    void stringExample_shouldNotBeQuoted() {
      // given
      var schema = new Schema<>();
      schema.setType("string");
      schema.setExample("foo");

      var parameter = new Parameter();
      parameter.setSchema(schema);
      parameter.setName("myParameter");
      parameter.setIn("query");

      // when
      var property = ParameterUtil.transformToProperty(parameter, null);

      // then
      assertThat(property.example()).isEqualTo("foo");
    }
  }
}
