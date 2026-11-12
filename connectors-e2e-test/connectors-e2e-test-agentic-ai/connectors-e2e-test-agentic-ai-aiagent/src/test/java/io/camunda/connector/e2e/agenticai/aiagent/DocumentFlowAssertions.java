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
package io.camunda.connector.e2e.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import java.util.List;

/** Assertions on the document flow that hold for every scenario and every model provider. */
public final class DocumentFlowAssertions {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private DocumentFlowAssertions() {}

  /**
   * Asserts that no request sent to the model carries a document reference field. The model only
   * ever sees document ids, never store ids or content hashes. No tool schema of the requests
   * carries the document marker either: the model provider only knows standard JSON schema types
   * and formats.
   */
  public static void assertNoDocumentInternalsInLlmRequests(List<RecordedChatRequest> requests) {
    assertThat(requests).as("recorded LLM requests").isNotEmpty();
    for (int i = 0; i < requests.size(); i++) {
      final var requestNumber = i + 1;
      final var request = requests.get(i);
      assertThat(request.rawBody())
          .as("LLM request %d", requestNumber)
          .doesNotContain("storeId", "contentHash", "camunda.document.type");
      request
          .toolDefinitions()
          .forEach(tool -> {
            final var schema = OBJECT_MAPPER.valueToTree(tool.inputSchema());
            assertThat(schemaTypes(schema))
                .as("schema types of tool '%s' in LLM request %d", tool.name(), requestNumber)
                .doesNotContain("document");
            assertThat(schema.toString())
                .as("schema of tool '%s' in LLM request %d", tool.name(), requestNumber)
                .doesNotContain("camunda-document-id");
          });
    }
  }

  private static List<String> schemaTypes(JsonNode schema) {
    return schema.findValues("type").stream().map(JsonNode::asText).toList();
  }
}
