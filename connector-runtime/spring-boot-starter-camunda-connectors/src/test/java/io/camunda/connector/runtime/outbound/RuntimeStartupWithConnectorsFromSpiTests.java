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
package io.camunda.connector.runtime.outbound;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false"
    },
    classes = {TestConnectorRuntimeApplication.class})
@CamundaSpringProcessTest
@SlowTest
class RuntimeStartupWithConnectorsFromSpiTests {

  @Autowired private JobWorkerManager jobWorkerManager;

  @Test
  public void httpConnectorLoadedViaSpi() {
    Optional<JobWorkerValue> httpjson = jobWorkerManager.findJobWorkerConfigByName("TEST");
    assertTrue(httpjson.isPresent());
  }
}
