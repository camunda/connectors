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
package io.camunda.connector.runtime.jobworker;

import static io.camunda.connector.runtime.jobworker.ConnectorJobHandler.RESULT_EXPRESSION_HEADER_NAME;
import static io.camunda.connector.runtime.jobworker.ConnectorJobHandler.RESULT_VARIABLE_HEADER_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import java.util.Collections;
import java.util.Map;
import org.mockito.ArgumentCaptor;

class JobBuilder {

  public static class JobBuilderStep {

    private final JobClient jobClient;
    private final ActivatedJob job;
    private final CompleteJobCommandStep1 completeCommand;
    private final FailJobCommandStep1 failCommand;

    public JobBuilderStep() {

      this.jobClient = mock(JobClient.class);
      this.job = mock(ActivatedJob.class);
      this.completeCommand = mock(CompleteJobCommandStep1.class, RETURNS_DEEP_STUBS);
      this.failCommand = mock(FailJobCommandStep1.class, RETURNS_DEEP_STUBS);

      when(jobClient.newCompleteCommand(any())).thenReturn(completeCommand);
      when(jobClient.newFailCommand(any())).thenReturn(failCommand);
      when(job.getKey()).thenReturn(-1l);
    }

    public JobBuilderStep withHeaders(Map<String, String> headers) {
      when(job.getCustomHeaders()).thenReturn(headers);

      return this;
    }

    public JobBuilderStep withResultVariableHeader(final String value) {
      return withHeader(RESULT_VARIABLE_HEADER_NAME, value);
    }

    public JobBuilderStep withResultExpressionHeader(final String value) {
      return withHeader(RESULT_EXPRESSION_HEADER_NAME, value);
    }

    public JobBuilderStep withHeader(String key, String value) {
      return withHeaders(Map.of(key, value));
    }

    public JobResult execute(ConnectorJobHandler connectorJobHandler) {
      return execute(connectorJobHandler, true);
    }

    public JobResult execute(ConnectorJobHandler connectorJobHandler, boolean expectComplete) {

      // when
      connectorJobHandler.handle(jobClient, job);

      if (expectComplete) {
        var variablesCaptor = ArgumentCaptor.forClass(Map.class);
        // then
        verify(completeCommand).variables(variablesCaptor.capture());
        return new JobResult(variablesCaptor.getValue());
      } else {
        verify(failCommand).retries(0);
        return new JobResult(Collections.emptyMap());
      }
    }
  }

  public static class JobResult {

    private final Map<String, Object> variables;

    public JobResult(Map<String, Object> variables) {
      this.variables = variables;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }

    public Object getVariable(String key) {
      return variables.get(key);
    }
  }

  static JobBuilderStep create() {
    return new JobBuilderStep();
  }
}
