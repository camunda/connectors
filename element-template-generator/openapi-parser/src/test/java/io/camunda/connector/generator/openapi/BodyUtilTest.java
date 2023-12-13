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

import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.openapi.util.BodyUtil;
import io.camunda.connector.generator.openapi.util.BodyUtil.BodyParseResult.Raw;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class BodyUtilTest {

  @Test
  void complexBodyObject_parsedAsRaw() throws JSONException {
    // given
    // object with nested object
    var complexObjectSchema =
        new Schema<>()
            .type("object")
            .properties(
                Map.of(
                    "foo",
                    new Schema<>()
                        .type("object")
                        .properties(Map.of("bar", new Schema<>().type("string")))));

    var requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType("application/json", new MediaType().schema(complexObjectSchema)));

    var components = new Components();
    var options = new OpenApiGenerationSource.Options(false);

    // when
    var result = BodyUtil.parseBody(requestBody, components, options);

    // then
    assertThat(result).isInstanceOf(BodyUtil.BodyParseResult.Raw.class);
    JSONAssert.assertEquals(
        """
        {
          "foo": {
            "bar": "string"
          }
        }
        """,
        ((Raw) result).rawBody(),
        true);
  }

  @Test
  void simpleBodyObject_parsedAsDetailed() {
    // given
    var simpleObjectSchema =
        new Schema<>().type("object").properties(Map.of("foo", new Schema<>().type("string")));

    var requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType("application/json", new MediaType().schema(simpleObjectSchema)));

    var components = new Components();
    var options = new OpenApiGenerationSource.Options(false);

    // when
    var result = BodyUtil.parseBody(requestBody, components, options);

    // then
    assertThat(result).isInstanceOf(BodyUtil.BodyParseResult.Detailed.class);
    var detailedResult = (BodyUtil.BodyParseResult.Detailed) result;
    assertThat(detailedResult.feelBuilder().build()).isEqualTo("={foo:foo}");
    assertThat(detailedResult.properties()).hasSize(1);
    assertThat(detailedResult.properties().get(0).id()).isEqualTo("foo");
    assertThat(detailedResult.properties().get(0).target())
        .isEqualTo(HttpOperationProperty.Target.BODY);
    assertThat(detailedResult.properties().get(0).type())
        .isEqualTo(HttpOperationProperty.Type.STRING);
  }

  @Test
  void simpleBodyObject_configOverride_parsedAsRaw() throws JSONException {
    // given
    var simpleObjectSchema =
        new Schema<>().type("object").properties(Map.of("foo", new Schema<>().type("string")));

    var requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType("application/json", new MediaType().schema(simpleObjectSchema)));

    var components = new Components();
    var options = new OpenApiGenerationSource.Options(true);

    // when
    var result = BodyUtil.parseBody(requestBody, components, options);

    // then
    assertThat(result).isInstanceOf(BodyUtil.BodyParseResult.Raw.class);
    JSONAssert.assertEquals(
        """
        {
          "foo": "string"
        }
        """,
        ((Raw) result).rawBody(),
        true);
  }
}
