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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.util.StreamUtils.copyToByteArray;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      WebhookNotActivatedDocumentTests.SpyTestConfig.class
    },
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
@Import(WebhookNotActivatedDocumentTests.SpyTestConfig.class)
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
public class WebhookNotActivatedDocumentTests {

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
  @Autowired MockMvc mockMvc;

  @Autowired ProcessStateManager stateStore;

  @MockitoBean SearchQueryClient searchQueryClient;

  @Autowired DocumentFactory documentFactory;

  @LocalServerPort int serverPort;

  @Test
  void shouldNotCreateDocumentsAndReturnResponse_whenMultipartRequestButWontActivate()
      throws Exception {
    var mockUrl = "http://localhost:" + serverPort + "/inbound/testId";

    var model =
        replace(
            "webhook_document.bpmn",
            // invalid activation condition
            BpmnFile.Replace.replace(
                "<ACTIVATION_CONDITION>", "=request.headers.THEHEADER = &#34;INVALID_VALUE&#34;"));

    // Prepare a mocked process connectorData backed by our test model
    when(searchQueryClient.getProcessModel(2L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getProcessDefinitionKey()).thenReturn(2L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());

    // Deploy the webhook
    stateStore.update(
        new ImportResult(
            Map.of(
                new ProcessDefinitionRef(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                Collections.singleton(processDef.getProcessDefinitionKey())),
            ImportResult.ImportType.LATEST_VERSIONS));

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
          TimeUnit.SECONDS);
      future.get(10, TimeUnit.SECONDS);
      verify(documentFactory, never()).create(any());
    }
  }
}
