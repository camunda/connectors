/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalToolboxProcessDefinitionResolverTest {

  private static final String PROCESS_ID = "toolbox-process";
  private static final RetriesProperties RETRIES = new RetriesProperties(2, Duration.ofMillis(10));

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  private LocalToolboxProcessDefinitionResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new LocalToolboxProcessDefinitionResolver(camundaClient, RETRIES);
  }

  @Test
  void resolvesLatestVersion_whenVersionNotSpecified() {
    var v1 = processDefinition(1, 100L);
    var v2 = processDefinition(2, 200L);
    when(camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(List.of(v1, v2));

    var result = resolver.resolveProcessDefinitionKey(PROCESS_ID, null);

    assertThat(result).isEqualTo(200L);
  }

  @Test
  void resolvesPinnedVersion_whenVersionSpecified() {
    var v1 = processDefinition(1, 100L);
    var v2 = processDefinition(2, 200L);
    when(camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(List.of(v1, v2));

    var result = resolver.resolveProcessDefinitionKey(PROCESS_ID, 1);

    assertThat(result).isEqualTo(100L);
  }

  @Test
  void throwsException_whenNoProcessDefinitionsFound() {
    when(camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(List.of());

    assertThatThrownBy(() -> resolver.resolveProcessDefinitionKey(PROCESS_ID, null))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("LOCAL_TOOLBOX_PROCESS_DEFINITION_NOT_FOUND");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "No deployed process definition found with process id 'toolbox-process'.");
            });
  }

  @Test
  void throwsException_whenPinnedVersionNotFound() {
    var v1 = processDefinition(1, 100L);
    when(camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(List.of(v1));

    assertThatThrownBy(() -> resolver.resolveProcessDefinitionKey(PROCESS_ID, 5))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("LOCAL_TOOLBOX_PROCESS_DEFINITION_NOT_FOUND");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "No deployed process definition found with process id 'toolbox-process' and version 5.");
            });
  }

  private ProcessDefinition processDefinition(int version, long key) {
    // lenient: not every scenario exercises both accessors for every list entry (e.g. version
    // lookup short-circuits on first match; the no-version branch never reads getVersion() at all)
    var processDefinition = org.mockito.Mockito.mock(ProcessDefinition.class);
    lenient().when(processDefinition.getVersion()).thenReturn(version);
    lenient().when(processDefinition.getProcessDefinitionKey()).thenReturn(key);
    return processDefinition;
  }
}
