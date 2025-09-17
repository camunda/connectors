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
package io.camunda.connector.uniquet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

class IndexWriterTest {

  @Test
  void persist() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Path tempDir = Files.createTempDirectory("test");
    Path filePath = tempDir.resolve("testFile.json");
    try (MockedStatic<FileHelper> mockedStatic = mockStatic(FileHelper.class)) {
      when(FileHelper.getCurrentGitSha256(anyString())).thenReturn("currentGitSha256");
      when(FileHelper.getBaseName(any())).thenCallRealMethod();
      when(FileHelper.toJsonNode(any())).thenCallRealMethod();
      mockedStatic.when(() -> FileHelper.writeToFile(any(), anyMap())).thenCallRealMethod();
      mockedStatic
          .when(() -> FileHelper.writeToFile(any(), ArgumentMatchers.any(JsonNode.class)))
          .thenCallRealMethod();
      IndexWriter.create("", "src/test/resources", filePath, null).persist();
      JsonNode result = mapper.readTree(new File(filePath.toUri())).get("io.camunda:soap");
      assertTrue(result.isArray());
      ArrayNode jsonArray = (ArrayNode) result;
      // versioned took priority
      assertEquals(1, jsonArray.size());
      assertEquals(
          "https://raw.githubusercontent.com/camunda/connectors/currentGitSha256/src/test/resources/connectors/element-templates/versioned/soap-outbound-connector-2.json",
          jsonArray.get(0).get("ref").asText());
    }
  }
}
