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

import static io.camunda.connector.test.docker.DockerImages.RABBITMQ;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.test.docker.DockerImages;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class BaseRabbitMqTest {
  protected static final String ELEMENT_ID = "elementId";
  protected static final String OUTBOUND_ELEMENT_TEMPLATE_PATH =
      "../../connectors/rabbitmq/element-templates/rabbitmq-outbound-connector.json";
  protected static final String INTERMEDIATE_CATCH_EVENT_BPMN = "intermediate-catch-event.bpmn";
  public static final String RABBITMQ_TEST_IMAGE = DockerImages.get(RABBITMQ);

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  @MockitoBean ProcessDefinitionSearch processDefinitionSearch;

  @MockitoBean SearchQueryClient searchQueryClient;

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  protected ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(camundaClient).deploy(updatedModel).createInstance();
  }
}
