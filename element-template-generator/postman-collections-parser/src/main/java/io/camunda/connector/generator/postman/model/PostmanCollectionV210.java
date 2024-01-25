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
package io.camunda.connector.generator.postman.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Endpoint.Request.Method;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210.Item.Folder;
import io.camunda.connector.generator.postman.utils.ObjectMapperProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PostmanCollectionV210(
    Info info,
    @JsonProperty("item") List<JsonNode> itemsAsJsonNode,
    @JsonProperty("variable") List<Variable> variables,
    @JsonProperty("auth") JsonNode authAsJsonNode) {

  public record Info(
      String name,
      String postmanId,
      @JsonProperty("description") JsonNode descriptionAsJsonNode,
      @JsonProperty("versionAsJsonNode") JsonNode versionAsJsonNode,
      String schema) {

    public Description description() {
      return parseDescription(this.descriptionAsJsonNode);
    }
  }

  public sealed interface Item {

    String name();

    record Endpoint(
        String id,
        String name,
        Description description,
        List<Variable> variables,
        @JsonProperty("request") JsonNode requestAsJsonNode)
        implements Item {
      public Request request() {
        if (requestAsJsonNode.isObject()) {
          return ObjectMapperProvider.getInstance().convertValue(requestAsJsonNode, Request.class);
        } else {
          return new Request(requestAsJsonNode, null, Method.GET, null, null, null);
        }
      }

      public record Request(
          @JsonProperty("url") JsonNode urlAsJsonNode,
          Auth auth,
          Method method,
          @JsonProperty("description") JsonNode descriptionAsJsonNode,
          @JsonProperty("header") JsonNode headerAsJsonNode,
          @JsonProperty("body") JsonNode bodyAsJsonNode) {

        public Description description() {
          return parseDescription(this.descriptionAsJsonNode);
        }

        public Url url() {
          if (urlAsJsonNode == null) {
            return new Url("", null, List.of("{{host}}"), Collections.emptyList(), null, null);
          }
          if (urlAsJsonNode.isObject()) {
            return ObjectMapperProvider.getInstance().convertValue(urlAsJsonNode, Url.class);
          } else {
            return new Url(
                urlAsJsonNode.asText(),
                null,
                List.of("{{host}}"),
                Collections.emptyList(),
                null,
                null);
          }
        }

        public record Url(
            String raw,
            String protocol,
            List<String> host,
            List<String> path,
            String port,
            @JsonProperty("query") List<QueryParam> queryParams) {
          public record QueryParam(String key, String value) {}
        }

        public enum Method {
          GET,
          PUT,
          POST,
          PATCH,
          DELETE,
          COPY,
          HEAD,
          OPTIONS,
          LINK,
          UNLINK,
          PURGE,
          LOCK,
          UNLOCK,
          PROPFIND,
          VIEW
        }

        public record Body(
            BodyMode mode,
            String raw,
            Object graphql,
            @JsonProperty("urlencoded") JsonNode urlencodedAsJsonNode,
            Object formdata) {
          public enum BodyMode {
            raw,
            urlencoded,
            formdata,
            file,
            graphql
          }

          public List<UrlEncodedParameter> urlencoded() {
            if (urlencodedAsJsonNode == null || !urlencodedAsJsonNode.isArray()) {
              return Collections.emptyList();
            }

            return ObjectMapperProvider.getInstance()
                .convertValue(urlencodedAsJsonNode, new TypeReference<>() {});
          }

          public record UrlEncodedParameter(String key, String value) {}
        }

        public Body body() {
          if (bodyAsJsonNode == null) {
            return null;
          } else {
            return ObjectMapperProvider.getInstance().convertValue(bodyAsJsonNode, Body.class);
          }
        }

        public record Header(String key, String value, boolean disabled) {}

        public List<Header> headers() {
          if (headerAsJsonNode == null || !headerAsJsonNode.isArray()) {
            return Collections.emptyList();
          }

          return ObjectMapperProvider.getInstance()
              .convertValue(headerAsJsonNode, new TypeReference<>() {});
        }
      }
    }

    record Folder(String name, Description description, List<Variable> variables, List<Item> items)
        implements Item {}
  }

  public List<Item> items() {
    List<PostmanItem> collectionItems =
        ObjectMapperProvider.getInstance().convertValue(itemsAsJsonNode, new TypeReference<>() {});
    return traverseAndConvertItems(collectionItems);
  }

  private record PostmanItem(
      String id,
      String name,
      @JsonProperty("description") JsonNode descriptionAsJsonNode,
      @JsonProperty("variable") List<Variable> variables,
      JsonNode request,
      JsonNode response,
      List<PostmanItem> item) {}

  private static List<Item> traverseAndConvertItems(List<PostmanItem> postmanItems) {
    if (postmanItems == null || postmanItems.isEmpty()) {
      return null;
    }
    List<Item> itemsPlaceholder = new ArrayList<>();
    for (PostmanItem postmanItem : postmanItems) {
      if (postmanItem.request != null) {
        // Endpoint
        itemsPlaceholder.add(
            new Endpoint(
                postmanItem.id,
                postmanItem.name,
                parseDescription(postmanItem.descriptionAsJsonNode),
                postmanItem.variables,
                postmanItem.request));
      } else if (postmanItem.item != null && !postmanItem.item.isEmpty()) {
        // Folder with sub-items
        itemsPlaceholder.add(
            new Folder(
                postmanItem.name,
                parseDescription(postmanItem.descriptionAsJsonNode),
                postmanItem.variables,
                traverseAndConvertItems(postmanItem.item)));
      } else {
        // Ignore, empty folder
      }
    }
    return itemsPlaceholder;
  }

  public record Auth(
      Type type,
      List<AuthEntry> noauth,
      List<AuthEntry> apikey,
      List<AuthEntry> awsv4,
      List<AuthEntry> basic,
      List<AuthEntry> bearer,
      List<AuthEntry> digest,
      List<AuthEntry> edgegrid,
      List<AuthEntry> hawk,
      List<AuthEntry> oauth1,
      List<AuthEntry> oauth2,
      List<AuthEntry> ntlm) {

    public record AuthEntry(String key, String value) {}

    public enum Type {
      apikey,
      awsv4,
      basic,
      bearer,
      digest,
      edgegrid,
      hawk,
      noauth,
      oauth1,
      oauth2,
      ntlm
    }
  }

  public Auth auth() {
    if (this.authAsJsonNode != null) {
      return ObjectMapperProvider.getInstance().convertValue(this.authAsJsonNode, Auth.class);
    }
    return null;
  }

  public record Variable(
      String id,
      String key,
      JsonNode value,
      Type type,
      String name,
      @JsonProperty("description") JsonNode descriptionAsJsonNode) {

    public Description description() {
      return parseDescription(this.descriptionAsJsonNode);
    }

    public enum Type {
      STRING("string"),
      BOOLEAN("boolean"),
      ANY("any"),
      NUMBER("number");
      private final String value;

      Type(String value) {
        this.value = value;
      }

      public String value() {
        return this.value;
      }
    }
  }

  public record Description(String content, String type) {}

  private static Description parseDescription(JsonNode description) {
    if (description == null) {
      return null;
    }

    if (description.isObject()) {
      return ObjectMapperProvider.getInstance().convertValue(description, Description.class);
    } else {
      return new Description(description.asText(), "text/plain");
    }
  }
}
