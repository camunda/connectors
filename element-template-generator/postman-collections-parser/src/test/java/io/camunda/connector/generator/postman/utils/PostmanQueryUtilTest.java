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
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request.Url;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request.Url.QueryParam;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class PostmanQueryUtilTest {

  @Test
  void transformToQueryParamProperty_PreSetParam_DefinedAsProperty() {
    QueryParam q = new QueryParam("isTest", "yes");
    Url url = new Url(null, null, null, null, null, List.of(q));
    Request request =
        new Request(
            ObjectMapperProvider.getInstance().convertValue(url, JsonNode.class),
            null,
            null,
            null,
            null,
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanQueryUtil.transformToQueryParamProperty(endpoint);

    assertThat(props).hasSize(1);
    var prop = (HttpOperationProperty) props.iterator().next();
    assertThat(prop.target()).isEqualTo(Target.QUERY);
    assertThat(prop.id()).isEqualTo(q.key());
    assertThat(prop.example()).isEqualTo(q.value());
  }

  @Test
  void transformToQueryParamProperty_MultiplePresetQueryParams_DefinedAsProperty() {
    QueryParam q1 = new QueryParam("isTest", "yes");
    QueryParam q2 = new QueryParam("encoding", "utf8");
    Url url = new Url(null, null, null, null, null, List.of(q1, q2));
    Request request =
        new Request(
            ObjectMapperProvider.getInstance().convertValue(url, JsonNode.class),
            null,
            null,
            null,
            null,
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanQueryUtil.transformToQueryParamProperty(endpoint);

    assertThat(props).hasSize(2);
    assertThat(props).allMatch(prop -> prop.target().equals(Target.QUERY));
  }

  @Test
  void transformToQueryParamProperty_VariableQuery_DefinedAsProperty() {
    QueryParam q = new QueryParam("isTest", "{{i_am_variable}}");
    Url url = new Url(null, null, null, null, null, List.of(q));
    Request request =
        new Request(
            ObjectMapperProvider.getInstance().convertValue(url, JsonNode.class),
            null,
            null,
            null,
            null,
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanQueryUtil.transformToQueryParamProperty(endpoint);

    assertThat(props).hasSize(1);
    var prop = (HttpOperationProperty) props.iterator().next();
    assertThat(prop.target()).isEqualTo(Target.QUERY);
    assertThat(prop.id()).isEqualTo(q.key());
    assertThat(prop.example()).isEqualTo("i_am_variable");
  }

  @Test
  void transformToQueryParamProperty_NoQueryParam_DoesNotFail() {
    Url url = new Url(null, null, null, null, null, null);
    Request request =
        new Request(
            ObjectMapperProvider.getInstance().convertValue(url, JsonNode.class),
            null,
            null,
            null,
            null,
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanQueryUtil.transformToQueryParamProperty(endpoint);

    assertThat(props).isEmpty();
  }

  @Test
  void transformToQueryParamProperty_NoValueDefined_DefinedAsEmptyProperty() {
    QueryParam q = new QueryParam("isTest", null);
    Url url = new Url(null, null, null, null, null, List.of(q));
    Request request =
        new Request(
            ObjectMapperProvider.getInstance().convertValue(url, JsonNode.class),
            null,
            null,
            null,
            null,
            null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var props = PostmanQueryUtil.transformToQueryParamProperty(endpoint);

    assertThat(props).hasSize(1);
    var prop = (HttpOperationProperty) props.iterator().next();
    assertThat(prop.target()).isEqualTo(Target.QUERY);
    assertThat(prop.id()).isEqualTo(q.key());
    assertThat(prop.example()).isEqualTo(StringUtils.EMPTY);
  }
}
