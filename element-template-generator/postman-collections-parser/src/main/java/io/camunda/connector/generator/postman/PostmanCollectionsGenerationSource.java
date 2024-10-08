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
package io.camunda.connector.generator.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.connector.generator.postman.model.PostmanCollectionV210;
import io.camunda.connector.generator.postman.utils.ObjectMapperProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record PostmanCollectionsGenerationSource(
    PostmanCollectionV210 collection, Set<String> includeOperations) {

  public PostmanCollectionsGenerationSource(List<String> cliParams) {
    this(fetchPostmanCollection(cliParams), extractOperationIds(cliParams));
  }

  private static PostmanCollectionV210 fetchPostmanCollection(List<String> cliParams) {
    if (cliParams.isEmpty()) {
      throw new IllegalArgumentException("Incorrect usage");
    }

    final var collectionPathOrContent = cliParams.getFirst();

    JsonNode collectionNode =
        Optional.ofNullable(collectionPathOrContent)
            .map(
                pathOrContent -> {
                  try {
                    if (isValidJSON(pathOrContent)) {
                      try {
                        return ObjectMapperProvider.getInstance()
                            .readValue(pathOrContent, JsonNode.class);
                      } catch (IOException e) {
                        throw new IllegalArgumentException(
                            "Couldn't parse Postman Collection to v.2.1.0 standard", e);
                      }
                    } else if (isValidYAML(pathOrContent)) {
                      final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                      mapper.findAndRegisterModules();
                      return mapper.readValue(pathOrContent, JsonNode.class);
                    }
                    final File postmanCollectionsFileJson;
                    if (pathOrContent.startsWith("http")) { // Network
                      postmanCollectionsFileJson = File.createTempFile("postman-gen", "tmp");
                      postmanCollectionsFileJson.deleteOnExit();
                      InputStream collectionUrl = new URL(collectionPathOrContent).openStream();
                      Files.copy(
                          collectionUrl,
                          postmanCollectionsFileJson.toPath(),
                          StandardCopyOption.REPLACE_EXISTING);
                    } else { // Local file system
                      postmanCollectionsFileJson = new File(collectionPathOrContent);
                    }
                    if (!postmanCollectionsFileJson.exists()
                        || !postmanCollectionsFileJson.isFile()) {
                      throw new IllegalArgumentException(
                          "Incorrect Postman Collections file: "
                              + postmanCollectionsFileJson.getName());
                    }
                    return ObjectMapperProvider.getInstance()
                        .readValue(new FileInputStream(postmanCollectionsFileJson), JsonNode.class);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Postman file path, URL, or content must be provided as first parameter"));

    try {
      PostmanCollectionV210 collection;
      // When collection shared directly from Postman UI
      if (collectionNode.has("collection")) {
        collection =
            ObjectMapperProvider.getInstance()
                .convertValue(collectionNode.get("collection"), PostmanCollectionV210.class);
      } else { // When collection was exported
        collection =
            ObjectMapperProvider.getInstance()
                .convertValue(collectionNode, PostmanCollectionV210.class);
      }

      if (collection.items() == null || collection.items().isEmpty()) {
        throw new IOException("Wasn't able to load items");
      }
      return collection;
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Couldn't parse Postman Collection to v.2.1.0 standard", e);
    }
  }

  public static boolean isValidJSON(String jsonInString) {
    try {
      ObjectMapperProvider.getInstance().readTree(jsonInString);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public static boolean isValidYAML(String yamlString) {
    try {
      final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      mapper.readTree(yamlString);
      return yamlString.contains("\n");
    } catch (IOException e) {
      return false;
    }
  }

  // 0th element is Postman Collections file; the 1st..Nth - operations
  private static Set<String> extractOperationIds(List<String> cliParams) {
    if (cliParams.size() == 1) {
      return Collections.emptySet();
    }

    Set<String> ops = new HashSet<>();
    for (int i = 1; i < cliParams.size(); i++) {
      ops.add(cliParams.get(i));
    }
    return ops;
  }
}
