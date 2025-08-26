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
package io.camunda.connector.e2e;

import static io.camunda.connector.e2e.BpmnFile.replace;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.util.StreamUtils.copyToByteArray;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.document.CamundaDocumentReferenceImpl;
import io.camunda.document.DocumentFactoryImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      WebhookActivatedDocumentTests.SpyTestConfig.class
    },
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@Import(WebhookActivatedDocumentTests.SpyTestConfig.class)
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
public class WebhookActivatedDocumentTests {

  @TestConfiguration
  static class SpyTestConfig {

    @Bean
    @Primary
    public DocumentFactory documentFactorySpied() {
      return spy(new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
    }
  }

  public static final String TEXT_FILE = "text.txt";
  public static final String PNG_FILE = "camunda1.png";
  @Autowired CamundaClient camundaClient;

  ObjectMapper mapper = new ObjectMapper();

  @Autowired MockMvc mockMvc;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @Autowired ProcessStateStore stateStore;

  @MockBean SearchQueryClient searchQueryClient;

  @Autowired DocumentFactory documentFactory;

  @LocalServerPort int serverPort;

  @BeforeEach
  void beforeAll() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  @Test
  void shouldCreateDocumentsAndReturnResponse_whenMultipartRequest() throws Exception {
    var mockUrl = "http://localhost:" + serverPort + "/inbound/testId";
    var model =
        replace(
            "webhook_document.bpmn",
            // valid activation condition
            BpmnFile.Replace.replace(
                "<ACTIVATION_CONDITION>", "=request.headers.THEHEADER = &#34;THEVALUE&#34;"));

    // Prepare a mocked process connectorData backed by our test model
    when(searchQueryClient.getProcessModel(1L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getProcessDefinitionKey()).thenReturn(1L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());

    // Deploy the webhook
    stateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessDefinitionIdentifier(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                new ProcessDefinitionVersion(
                    processDef.getProcessDefinitionKey(), processDef.getVersion()))));

    var bpmnTest = ZeebeTest.with(camundaClient).deploy(model).createInstance();
    CompletableFuture<ResultActions> future = new CompletableFuture<>();
    ClassPathResource textFile = new ClassPathResource("files/text.txt");
    ClassPathResource imageFile = new ClassPathResource("files/camunda1.png");
    byte[] textFileContent = copyToByteArray(textFile.getInputStream());
    byte[] imageFileContent = copyToByteArray(imageFile.getInputStream());

    try (var executor = Executors.newSingleThreadScheduledExecutor()) {
      executor.schedule(
          () -> {
            try {
              future.complete(
                  mockMvc.perform(
                      multipart(mockUrl)
                          .part(
                              new MockPart(
                                  "param1", PNG_FILE, imageFileContent, MediaType.IMAGE_PNG))
                          .part(
                              new MockPart(
                                  "param2", TEXT_FILE, textFileContent, MediaType.TEXT_PLAIN))
                          .header("THEHEADER", "THEVALUE")));
            } catch (Exception e) {
              future.completeExceptionally(e);
            }
          },
          2,
          java.util.concurrent.TimeUnit.SECONDS);
      var result = bpmnTest.waitForProcessCompletion();

      assertThat(result.getProcessInstanceEvent()).hasVariable("body", Map.of());
      assertThat(result.getProcessInstanceEvent()).isCompleted();
      var resultActions = future.get(10, TimeUnit.SECONDS);
      var response = resultActions.andExpect(status().isOk()).andReturn();
      String jsonResponse = response.getResponse().getContentAsString();
      Map<String, Object> actualResponse = mapper.readValue(jsonResponse, Map.class);
      List<Map> documents = (List<Map>) actualResponse.get("documents");

      assertThat(result.getProcessInstanceEvent()).hasVariable("documents", documents);

      verify(documentFactory, times(2)).create(any());
      Assertions.assertThat(documents).hasSize(2);
      Map<String, Object> pngDocument =
          (Map<String, Object>) ((List<?>) actualResponse.get("documents")).get(0);
      Assertions.assertThat(pngDocument).containsKey("storeId");
      Assertions.assertThat(pngDocument).containsKey("documentId");
      Map<String, Object> metadata = (Map<String, Object>) pngDocument.get("metadata");
      Assertions.assertThat(metadata.get("fileName")).isEqualTo(PNG_FILE);
      Assertions.assertThat(metadata.get("contentType")).isEqualTo(MediaType.IMAGE_PNG_VALUE);
      Map<String, Object> textDocument =
          (Map<String, Object>) ((List<?>) actualResponse.get("documents")).get(1);
      Assertions.assertThat(textDocument).containsKey("storeId");
      Assertions.assertThat(textDocument).containsKey("documentId");
      Map<String, Object> metadata2 = (Map<String, Object>) textDocument.get("metadata");
      Assertions.assertThat(metadata2.get("fileName")).isEqualTo(TEXT_FILE);
      Assertions.assertThat(metadata2.get("contentType")).isEqualTo(MediaType.TEXT_PLAIN_VALUE);

      var pngStoredDocument =
          documentFactory.resolve(
              new CamundaDocumentReferenceImpl(
                  pngDocument.get("storeId").toString(),
                  pngDocument.get("documentId").toString(),
                  pngDocument.get("contentHash").toString(),
                  null));
      var storedContent = pngStoredDocument.asByteArray();
      Assertions.assertThat(storedContent).isEqualTo(imageFileContent);

      var textStoredDocument =
          documentFactory.resolve(
              new CamundaDocumentReferenceImpl(
                  textDocument.get("storeId").toString(),
                  textDocument.get("documentId").toString(),
                  textDocument.get("contentHash").toString(),
                  null));
      var storedTextContent = textStoredDocument.asByteArray();
      Assertions.assertThat(new String(storedTextContent))
          .isEqualTo("Hello from\n" + "the Camunda Connectors!");
    }
  }
}
