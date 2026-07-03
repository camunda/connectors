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
package io.camunda.connector.e2e.agenticai.adhoctoolsschema;

import io.camunda.connector.test.utils.annotation.SlowTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.discovery.enabled=false"})
public class AdHocToolsSchemaMcpDiscoveryDisabledTests extends BaseAdHocToolsSchemaTests {

  @Value("classpath:agentic-ai-connectors-mcp.bpmn")
  private Resource processMcp;

  @Value("classpath:expected-schema-result-mcp-discovery-disabled.json")
  private Resource expectedSchemaResultMcpDiscoveryDisabled;

  @Test
  void loadsAdHocToolsSchemaWithMcpDiscoveryDisabled() throws IOException {
    testAdHocToolSchema(processMcp, expectedSchemaResultMcpDiscoveryDisabled);
  }
}
