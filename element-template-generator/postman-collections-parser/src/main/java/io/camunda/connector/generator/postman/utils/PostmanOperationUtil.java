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

import io.camunda.connector.generator.dsl.http.HttpFeelBuilder;
import io.camunda.connector.generator.dsl.http.HttpOperation;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.OperationParseResult;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Folder;
import io.camunda.connector.generator.postman.utils.PostmanBodyUtil.BodyParseResult;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostmanOperationUtil {

  private static final Logger LOG = LoggerFactory.getLogger(PostmanOperationUtil.class);

  protected static final String POSTMAN_FOLDER_SEPARATOR = "/";
  protected static final String POSTMAN_VARIABLE_PREFIX_TAG = "{{";
  protected static final String POSTMAN_VARIABLE_SUFFIX_TAG = "}}";

  public static List<OperationParseResult> extractOperations(
      PostmanCollectionV210 collection, Set<String> includeOperations) {
    final var allOperationsRegistry = traverseCollection(collection);
    final var includedRequests = new HashMap<String, Endpoint>();

    // parse everything
    if (includeOperations.isEmpty()) {
      allOperationsRegistry.forEach(
          (name, item) -> {
            if (item instanceof Endpoint e) includedRequests.put(name, e);
          });
    }

    for (String includedOp : includeOperations) {
      final var opIdentifier = normalizeOperationName(includedOp);
      Item item = allOperationsRegistry.get(opIdentifier);
      if (item instanceof Endpoint e) {
        includedRequests.put(opIdentifier, e);
      } else {
        LOG.warn("Operation " + includedOp + " was not an endpoint, ignoring!");
      }
    }
    final var includedOperations = new ArrayList<OperationParseResult>();
    for (Entry<String, Endpoint> endpointEntry : includedRequests.entrySet()) {
      OperationParseResult res = extractOperation(endpointEntry.getKey(), endpointEntry.getValue());
      res.builder().pathFeelExpression(extractPath(res.path()));
      includedOperations.add(res);
    }
    return includedOperations;
  }

  // Postman collections are arranged as a folder structure
  // This code traverses a folder-tree recursively
  private static Map<String, Item> traverseCollection(PostmanCollectionV210 collection) {
    var operations = new HashMap<String, Item>();
    for (Item item : collection.items()) {
      operations.putAll(traverseCollectionNode(item, StringUtils.EMPTY));
    }
    return operations;
  }

  private static Map<String, Item> traverseCollectionNode(Item node, String prefix) {
    var operations = new HashMap<String, Item>();

    if (node instanceof Folder folder) {
      for (Item item : folder.items()) {
        operations.putAll(
            traverseCollectionNode(item, prefix + POSTMAN_FOLDER_SEPARATOR + folder.name()));
      }
    } else if (node instanceof Endpoint endpoint) {
      operations.put(
          normalizeOperationName(prefix + POSTMAN_FOLDER_SEPARATOR + endpoint.name()), node);
    } else {
      throw new RuntimeException("Item type not supported");
    }

    return operations;
  }

  private static HttpFeelBuilder extractPath(String rawPath) {
    // split path into parts, each part is either a variable or a constant
    String[] pathParts = rawPath.split("\\{");
    var builder = HttpFeelBuilder.string();
    if (pathParts.length == 1) {
      // no variables
      builder.part(rawPath);
    } else {
      for (String pathPart : pathParts) {
        if (pathPart.contains("}")) {
          String[] variableParts = pathPart.split("}");
          // replace dashes in variable names with underscores, same must be done for properties
          var property = variableParts[0].replace("-", "_");
          builder.property(property);
          if (variableParts.length > 1) {
            builder.part(variableParts[1]);
          }
        } else {
          builder.part(pathPart);
        }
      }
    }
    return builder;
  }

  private static OperationParseResult extractOperation(String opIdentifier, Endpoint endpoint) {
    String endpointUrl = PostmanPathUtil.extractPathFromUrl(endpoint);

    var label = endpoint.name();
    var operationId = TransformerUtils.normalizeString(label + " " + opIdentifier);
    List<String> tags = Arrays.asList(opIdentifier.split("/"));
    Set<HttpOperationProperty> requestConfigurationProps = new HashSet<>();
    requestConfigurationProps.addAll(PostmanPathUtil.transformToPathProperty(endpoint));
    requestConfigurationProps.addAll(PostmanQueryUtil.transformToQueryParamProperty(endpoint));
    requestConfigurationProps.addAll(PostmanHeaderUtil.transformToHeaderProperty(endpoint));

    var body = PostmanBodyUtil.parseBody(endpoint);
    HttpFeelBuilder bodyFeelExpression = null;
    if (body instanceof BodyParseResult.Raw rawBody) {
      bodyFeelExpression = HttpFeelBuilder.preFormatted("=" + rawBody.rawBody());
    } else if (body instanceof BodyParseResult.Detailed detailedBody) {
      bodyFeelExpression = detailedBody.feelBuilder();
      requestConfigurationProps.addAll(detailedBody.properties());
    }

    var opBuilder =
        HttpOperation.builder()
            .id(operationId)
            .label(label)
            // [FEATURE GAP] TODO: auth override
            .bodyFeelExpression(bodyFeelExpression)
            .method(HttpMethod.valueOf(endpoint.request().method().name()))
            .properties(requestConfigurationProps);

    return new OperationParseResult(
        operationId,
        endpointUrl,
        HttpMethod.valueOf(endpoint.request().method().name()),
        tags,
        true,
        null,
        opBuilder);
  }

  private static String normalizeOperationName(final String operationName) {
    return operationName.trim().toUpperCase();
  }
}
