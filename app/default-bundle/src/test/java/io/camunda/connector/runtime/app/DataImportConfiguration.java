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
package io.camunda.connector.runtime.app;

import io.camunda.client.CamundaClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

@Configuration
@Profile("data-import")
public class DataImportConfiguration {
  private CamundaClient client;

  private static final int VERSIONS_PER_PROCESS = 100;
  private static final int PROCESS_NUMBER = 500;

  public DataImportConfiguration(CamundaClient client) {
    this.client = client;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void testClient() {
    String processAsString = getProcessAsString();

    try (ExecutorService executorService = Executors.newFixedThreadPool(10)) {
      List<CompletableFuture<Void>> futures =
          IntStream.range(0, PROCESS_NUMBER)
              .mapToObj(
                  i ->
                      CompletableFuture.runAsync(
                          () -> {
                            System.out.println(
                                "Deploying process versions for ID: process-" + i + "...");
                            for (int j = 0; j < VERSIONS_PER_PROCESS; j++) {
                              final String processId = "process-" + i;

                              // this is required to ensure that a new version is created
                              String updatedProcess =
                                  updateActivityId(i, processAsString, processId, j);
                              deployProcess(updatedProcess, processId);
                              if (i % 10 == 0) {
                                // every 10th process deployment, wait for Zeebe to catch up
                                letZeebeCatchup();
                              }
                            }
                            System.out.println(
                                "...Deployed all process versions for ID: process-" + i);
                          },
                          executorService))
              .toList();

      // wait for all deployments to finish
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      executorService.shutdown();
    }
    System.out.println("All process versions deployed successfully.");
  }

  private void letZeebeCatchup() {
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void deployProcess(String updatedProcess, String processId) {
    client
        .newDeployResourceCommand()
        .addResourceStringUtf8(updatedProcess, processId + ".bpmn")
        .send()
        .join(60, TimeUnit.SECONDS);
  }

  private String updateActivityId(int i, String processAsString, String processId, int j) {
    return processAsString
        .replace("__PROCESS_ID__", processId)
        .replace("__ACTIVITY_NAME__", i + "--" + j);
  }

  private String getProcessAsString() {
    try (final InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream("data-import/QA Review Process.bpmn")) {
      Objects.requireNonNull(
          resourceStream, "Resource not found: data-import/QA Review Process.bpmn");
      return new String(resourceStream.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException("Failed to read BPMN resource", e);
    }
  }
}
