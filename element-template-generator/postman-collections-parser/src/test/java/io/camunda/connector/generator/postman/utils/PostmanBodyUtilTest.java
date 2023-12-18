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
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request.Body;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request.Body.BodyMode;
import io.camunda.connector.generator.postman.utils.PostmanBodyUtil.BodyParseResult;
import io.camunda.connector.generator.postman.utils.PostmanBodyUtil.BodyParseResult.Detailed;
import io.camunda.connector.generator.postman.utils.PostmanBodyUtil.BodyParseResult.Raw;
import org.junit.jupiter.api.Test;

class PostmanBodyUtilTest {

  @Test
  void parseBody_EmptyRawBody_ReturnEmptyRaw() {
    Body body = new Body(BodyMode.raw, "{}", null, null, null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo("");
  }

  @Test
  void parseBody_NullRawBody_ReturnEmptyRaw() {
    Request request = new Request(null, null, null, null, null, null);
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo("");
  }

  @Test
  void parseBody_BlankRawBody_ReturnEmptyRaw() {
    Body body = new Body(BodyMode.raw, "", null, null, null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo("");
  }

  @Test
  void parseBody_SimpleContentRawBody_ReturnDetailed() {
    Body body =
        new Body(
            BodyMode.raw,
            "{\"title\": \"One Hundred Years of Solitude\",\"author\": \"Gabriel García Márquez\",\"genre\": \"fiction\",\"yearPublished\": 1967}",
            null,
            null,
            null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Detailed.class);
    var detailedResult = (Detailed) result;
    assertThat(detailedResult.properties()).hasSize(4);
    assertThat(detailedResult.properties()).allMatch(prop -> prop.target().equals(Target.BODY));
  }

  @Test
  void parseBody_NestedRawBody_ReturnRaw() {
    var content =
        "{\"Project\": \"My Interesting Project\",\"devs\": [{\"name\":\"Bob\"},{\"name\":\"John\"}]}";
    Body body = new Body(BodyMode.raw, content, null, null, null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo(content);
  }

  @Test
  void parseBody_FormDataBody_ReturnEmptyRaw() {
    Body body = new Body(BodyMode.formdata, "[{\"key\":\"value\"}]", null, null, null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo("");
  }

  @Test
  void parseBody_UrlEncodedBody_ReturnEmptyRaw() {
    Body body = new Body(BodyMode.urlencoded, "a=b&c=d", null, null, null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo("");
  }

  @Test
  void parseBody_MalformedRawBody_ReturnEmptyRaw() {
    Body body = new Body(BodyMode.raw, "{\"x\"}", null, null, null);
    Request request =
        new Request(
            null,
            null,
            null,
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(body, JsonNode.class));
    Endpoint endpoint =
        new Endpoint(
            "Test endpoint",
            "Test name",
            null,
            null,
            ObjectMapperProvider.getInstance().convertValue(request, JsonNode.class));

    var result = PostmanBodyUtil.parseBody(endpoint);

    assertThat(result).isInstanceOf(BodyParseResult.Raw.class);
    assertThat(((Raw) result).rawBody()).isEqualTo("");
  }
}
