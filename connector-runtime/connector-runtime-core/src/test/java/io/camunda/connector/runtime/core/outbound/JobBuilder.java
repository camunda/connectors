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
package io.camunda.connector.runtime.core.outbound;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.Keywords;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;

class JobBuilder {

  protected static JobBuilderStep create() {
    return new JobBuilderStep();
  }

  public static class JobBuilderStep {

    private final ActivatedJob job;
    private final CompleteJobCommandStep1 completeCommand;
    private final FailJobCommandStep1 failCommand;
    private final FailJobCommandStep1.FailJobCommandStep2 failCommandStep2;
    private final ThrowErrorCommandStep1 throwCommand;
    private final ThrowErrorCommandStep1.ThrowErrorCommandStep2 throwCommandStep2;
    private final ThrowErrorCommandStep1.ThrowErrorCommandStep2 throwCommandStep2_2;
    private JobClient jobClient;

    public JobBuilderStep() {
      this.jobClient = mock(JobClient.class);
      this.job = mock(ActivatedJob.class);
      this.completeCommand = mock(CompleteJobCommandStep1.class, RETURNS_DEEP_STUBS);
      this.failCommand = mock(FailJobCommandStep1.class, RETURNS_DEEP_STUBS);
      this.failCommandStep2 =
          mock(FailJobCommandStep1.FailJobCommandStep2.class, RETURNS_DEEP_STUBS);
      this.throwCommand = mock(ThrowErrorCommandStep1.class, RETURNS_DEEP_STUBS);
      this.throwCommandStep2 =
          mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2.class, RETURNS_DEEP_STUBS);
      this.throwCommandStep2_2 =
          mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2.class, RETURNS_DEEP_STUBS);

      when(jobClient.newCompleteCommand(any())).thenReturn(completeCommand);
      when(jobClient.newFailCommand(any())).thenReturn(failCommand);
      when(failCommand.retries(anyInt())).thenReturn(failCommandStep2);
      when(failCommandStep2.errorMessage(anyString())).thenReturn(failCommandStep2);
      when(failCommandStep2.retryBackoff(any())).thenReturn(failCommandStep2);
      when(failCommandStep2.variables(any(Object.class))).thenReturn(failCommandStep2);
      when(failCommandStep2.variables(anyMap())).thenReturn(failCommandStep2);
      when(failCommandStep2.variables(anyString())).thenReturn(failCommandStep2);
      when(jobClient.newThrowErrorCommand(any())).thenReturn(throwCommand);
      when(throwCommand.errorCode(any())).thenReturn(throwCommandStep2);
      when(throwCommandStep2.variables(any(Map.class))).thenReturn(throwCommandStep2_2);
      when(job.getKey()).thenReturn(-1L);
    }

    public JobBuilderStep useJobClient(JobClient client) {
      this.jobClient = client;
      return this;
    }

    public JobBuilderStep withVariables(String variables) {
      when(job.getVariables()).thenReturn(variables);
      return this;
    }

    public JobBuilderStep withHeaders(Map<String, String> headers) {
      when(job.getCustomHeaders()).thenReturn(headers);
      return this;
    }

    public JobBuilderStep withRetries(int retries) {
      when(job.getRetries()).thenReturn(retries);
      return this;
    }

    public JobBuilderStep withResultVariableHeader(final String value) {
      return withHeader(Keywords.RESULT_VARIABLE_KEYWORD, value);
    }

    public JobBuilderStep withResultExpressionHeader(final String value) {
      return withHeader(Keywords.RESULT_EXPRESSION_KEYWORD, value);
    }

    public JobBuilderStep withErrorExpressionHeader(final String value) {
      return withHeader(Keywords.ERROR_EXPRESSION_KEYWORD, value);
    }

    public JobBuilderStep withHeader(String key, String value) {
      final Map<String, String> headers = new HashMap<>();
      headers.put(key, value);
      return withHeaders(headers);
    }

    public JobResult executeAndCaptureResult(ConnectorJobHandler connectorJobHandler) {
      return executeAndCaptureResult(connectorJobHandler, true, false);
    }

    public JobResult executeAndCaptureResult(
        ConnectorJobHandler connectorJobHandler, boolean expectComplete) {
      return executeAndCaptureResult(connectorJobHandler, expectComplete, false);
    }

    public JobResult executeAndCaptureResult(
        ConnectorJobHandler connectorJobHandler, boolean expectComplete, boolean expectBpmnError) {

      // when
      connectorJobHandler.handle(jobClient, job);

      if (expectComplete) {
        var variablesCaptor = ArgumentCaptor.forClass(Map.class);
        // then
        verify(completeCommand).variables(variablesCaptor.capture());
        return new JobResult(variablesCaptor.getValue());
      } else if (expectBpmnError) {
        // then
        var errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        var variablesCaptor = ArgumentCaptor.forClass(Map.class);
        var errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(throwCommand).errorCode(errorCodeCaptor.capture());
        verify(throwCommandStep2).variables(variablesCaptor.capture());
        verify(throwCommandStep2_2).errorMessage(errorMessageCaptor.capture());
        return new JobResult(
            errorCodeCaptor.getValue(), errorMessageCaptor.getValue(), variablesCaptor.getValue());
      } else {
        var errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        var variablesCaptor = ArgumentCaptor.forClass(Object.class);
        var retriesCaptor = ArgumentCaptor.forClass(Integer.class);
        // verify(failCommand).retries(job.getRetries() == 0 ? 0 : job.getRetries() - 1);
        verify(failCommandStep2).errorMessage(errorMessageCaptor.capture());
        verify(failCommandStep2).variables(variablesCaptor.capture());
        verify(failCommand).retries(retriesCaptor.capture());
        return new JobResult(
            errorMessageCaptor.getValue(),
            (Map) variablesCaptor.getValue(),
            retriesCaptor.getValue());
      }
    }

    public void execute(ConnectorJobHandler connectorJobHandler) {
      connectorJobHandler.handle(jobClient, job);
    }
  }

  public static class JobResult {

    private Map<String, Object> variables;
    private String errorCode;
    private String errorMessage;
    private int retries;

    public JobResult(Map<String, Object> variables) {
      this.variables = variables;
    }

    public JobResult(String errorCode, String errorMessage, Map<String, Object> variables) {
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
      this.variables = variables;
    }

    public JobResult(String errorMessage, Map<String, Object> variables, int retries) {
      this.errorMessage = errorMessage;
      this.variables = variables;
      this.retries = retries;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }

    public Object getVariable(String key) {
      return variables.get(key);
    }

    public String getErrorCode() {
      return errorCode;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public int getRetries() {
      return retries;
    }
  }
}
