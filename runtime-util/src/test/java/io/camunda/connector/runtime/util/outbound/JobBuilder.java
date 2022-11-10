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
package io.camunda.connector.runtime.util.outbound;

import static io.camunda.connector.runtime.util.ConnectorHelper.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;

class JobBuilder {

  public static class JobBuilderStep {

    private final JobClient jobClient;
    private final ActivatedJob job;
    private final CompleteJobCommandStep1 completeCommand;
    private final FailJobCommandStep1 failCommand;
    private final FailJobCommandStep1.FailJobCommandStep2 failCommandStep2;
    private final ThrowErrorCommandStep1 throwCommand;
    private final ThrowErrorCommandStep1.ThrowErrorCommandStep2 throwCommandStep2;

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

      when(jobClient.newCompleteCommand(any())).thenReturn(completeCommand);
      when(jobClient.newFailCommand(any())).thenReturn(failCommand);
      when(failCommand.retries(anyInt())).thenReturn(failCommandStep2);
      when(jobClient.newThrowErrorCommand(any())).thenReturn(throwCommand);
      when(throwCommand.errorCode(any())).thenReturn(throwCommandStep2);
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

    public JobBuilderStep withErrorExpressionHeader(final String value) {
      return withHeader(ERROR_EXPRESSION_HEADER_NAME, value);
    }

    public JobBuilderStep withHeader(String key, String value) {
      final Map<String, String> headers = new HashMap<>();
      headers.put(key, value);
      return withHeaders(headers);
    }

    public JobResult execute(ConnectorJobHandler connectorJobHandler) {
      return execute(connectorJobHandler, true, false);
    }

    public JobResult execute(ConnectorJobHandler connectorJobHandler, boolean expectComplete) {
      return execute(connectorJobHandler, expectComplete, false);
    }

    public JobResult execute(
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
        var errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(throwCommand).errorCode(errorCodeCaptor.capture());
        verify(throwCommandStep2).errorMessage(errorMessageCaptor.capture());
        return new JobResult(errorCodeCaptor.getValue(), errorMessageCaptor.getValue());
      } else {
        var errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(failCommand).retries(0);
        verify(failCommandStep2).errorMessage(errorMessageCaptor.capture());
        return new JobResult(null, errorMessageCaptor.getValue());
      }
    }
  }

  public static class JobResult {

    private Map<String, Object> variables;
    private String errorCode;
    private String errorMessage;

    public JobResult(Map<String, Object> variables) {
      this.variables = variables;
    }

    public JobResult(String errorCode, String errorMessage) {
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
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
  }

  protected static JobBuilderStep create() {
    return new JobBuilderStep();
  }
}
