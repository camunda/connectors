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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.dsl.http.HttpFeelBuilder;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import io.camunda.connector.generator.postman.utils.PostmanBodyUtil.BodyParseResult.Detailed;
import io.camunda.connector.generator.postman.utils.PostmanBodyUtil.BodyParseResult.Raw;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostmanBodyUtil {

  private static final Logger LOG = LoggerFactory.getLogger(PostmanBodyUtil.class);

  public sealed interface BodyParseResult permits BodyParseResult.Detailed, BodyParseResult.Raw {

    record Detailed(HttpFeelBuilder feelBuilder, List<HttpOperationProperty> properties)
        implements BodyParseResult {}

    record Raw(String rawBody) implements BodyParseResult {}
  }

  public static BodyParseResult parseBody(Endpoint endpoint) {
    if (endpoint.request().body() == null) {
      return new Raw("");
    }

    switch (endpoint.request().body().mode()) {
      case raw -> {
        return processPostmanRawBody(endpoint.request().body().raw());
      }
      case urlencoded, formdata, graphql, file -> {
        LOG.warn(
            "Error building endpoint '"
                + endpoint.name()
                + "'. Body type '"
                + endpoint.request().body().mode()
                + "' not supported");
        return new Raw("");
      }
      default -> {
        return new Raw("");
      }
    }
  }

  private static BodyParseResult processPostmanRawBody(String bodyContent) {
    if (StringUtils.isBlank(bodyContent) || "{}".equals(bodyContent)) {
      return new Raw("");
    }

    try {
      JsonNode parsedBody = ObjectMapperProvider.getInstance().readTree(bodyContent);
      boolean isComplex = false;
      var it = parsedBody.elements();
      while (it.hasNext()) {
        if (it.next().isContainerNode()) {
          isComplex = true;
        }
      }

      if (isComplex) {
        return new Raw(
            Optional.ofNullable(bodyContent)
                .orElse("")
                .replaceAll("\\{\\{", "")
                .replaceAll("}}", ""));
      } else {
        List<HttpOperationProperty> properties = new ArrayList<>();
        var contextBuilder = HttpFeelBuilder.context();

        var fields = parsedBody.fields();
        while (fields.hasNext()) {
          var node = fields.next();
          var property =
              HttpOperationProperty.createStringProperty(
                  node.getKey(), Target.BODY, StringUtils.EMPTY, true, node.getValue().asText());
          properties.add(property);
          contextBuilder.property(node.getKey(), property.id());
        }

        return new Detailed(contextBuilder, properties);
      }
    } catch (JsonProcessingException e) {
      LOG.warn("Wasn't able to parse body: " + bodyContent);
      return new Raw("");
    }
  }
}
