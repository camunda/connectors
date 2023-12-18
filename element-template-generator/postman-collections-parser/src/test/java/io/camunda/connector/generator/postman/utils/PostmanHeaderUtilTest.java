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
package io.camunda.connector.generator.postman.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request.Header;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class PostmanHeaderUtilTest {

  @Test
  void transformToHeaderProperty_PreSetHeader_DefinedAsProperty() {
    Header h = new Header("X-Camunda-Test", "Yes", false);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(List.of(h), JsonNode.class),
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanHeaderUtil.transformToHeaderProperty(endpoint);

    assertThat(props).hasSize(1);
    var prop = (HttpOperationProperty) props.iterator().next();
    assertThat(prop.target()).isEqualTo(Target.HEADER);
    assertThat(prop.id()).isEqualTo(h.key());
    assertThat(prop.example()).isEqualTo(h.value());
  }

  @Test
  void transformToHeaderProperty_MultiplePresetHeaders_DefinedAsProperty() {
    Header h1 = new Header("X-Camunda-Integ-Test", "No", false);
    Header h2 = new Header("X-Camunda-Unit-Test", "Yes", false);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(List.of(h1, h2), JsonNode.class),
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanHeaderUtil.transformToHeaderProperty(endpoint);

    assertThat(props).hasSize(2);
    assertThat(props).allMatch(prop -> prop.target().equals(Target.HEADER));
  }

  @Test
  void transformToHeaderProperty_VariableHeader_DefinedAsProperty() {
    Header h = new Header("X-Camunda-Test", "{{i_am_variable}}", false);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(List.of(h), JsonNode.class),
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanHeaderUtil.transformToHeaderProperty(endpoint);

    assertThat(props).hasSize(1);
    var prop = (HttpOperationProperty) props.iterator().next();
    assertThat(prop.target()).isEqualTo(Target.HEADER);
    assertThat(prop.id()).isEqualTo(h.key());
    assertThat(prop.example()).isEqualTo("i_am_variable");
  }

  @Test
  void transformToHeaderProperty_NoHeader_DoesNotFail() {
    Request request = new Request(null, null, null, null, null, null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanHeaderUtil.transformToHeaderProperty(endpoint);

    assertThat(props).isEmpty();
  }

  @Test
  void transformToHeaderProperty_NoValueDefined_DefinedAsEmptyProperty() {
    Header h = new Header("X-Camunda-Test", null, false);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(List.of(h), JsonNode.class),
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanHeaderUtil.transformToHeaderProperty(endpoint);

    assertThat(props).hasSize(1);
    var prop = (HttpOperationProperty) props.iterator().next();
    assertThat(prop.target()).isEqualTo(Target.HEADER);
    assertThat(prop.id()).isEqualTo(h.key());
    assertThat(prop.example()).isEqualTo(StringUtils.EMPTY);
  }
}
