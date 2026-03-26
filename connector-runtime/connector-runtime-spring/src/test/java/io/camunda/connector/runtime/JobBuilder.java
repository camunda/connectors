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
package io.camunda.connector.runtime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1.CompleteAdHocSubProcessResultStep2;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.runtime.JobBuilder.AdHocSubProcessJobResult.CapturedElementActivation;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.outbound.job.SpringConnectorJobHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class JobBuilder {

  public static JobBuilderStep create() {
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
      when(job.getType()).thenReturn("some-type");
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

    public JobResult executeAndCaptureResult(SpringConnectorJobHandler SpringConnectorJobHandler)
        throws Exception {
      return executeAndCaptureResult(SpringConnectorJobHandler, true, false);
    }

    public JobResult executeAndCaptureResult(
        SpringConnectorJobHandler springConnectorJobHandler, boolean expectComplete)
        throws Exception {
      return executeAndCaptureResult(springConnectorJobHandler, expectComplete, false);
    }

    public JobResult executeAndCaptureResult(
        SpringConnectorJobHandler SpringConnectorJobHandler,
        boolean expectComplete,
        boolean expectBpmnError)
        throws Exception {

      // when
      SpringConnectorJobHandler.handle(jobClient, job);

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

    @SuppressWarnings("unchecked")
    public AdHocSubProcessJobResult executeAndCaptureAdHocSubProcessResult(
        SpringConnectorJobHandler handler) throws Exception {

      // wire the complete command to return itself for variables(), so withResult() is reachable
      when(completeCommand.variables(anyMap())).thenReturn(completeCommand);

      // wire the AHSP builder chain
      var resultStep = mock(CompleteJobCommandJobResultStep.class);
      var ahspStep1 = mock(CompleteAdHocSubProcessResultStep1.class);
      var ahspStep2 = mock(CompleteAdHocSubProcessResultStep2.class);

      when(resultStep.forAdHocSubProcess()).thenReturn(ahspStep1);
      when(ahspStep1.completionConditionFulfilled(anyBoolean())).thenReturn(ahspStep1);
      when(ahspStep1.cancelRemainingInstances(anyBoolean())).thenReturn(ahspStep1);
      when(ahspStep1.activateElement(any())).thenReturn(ahspStep2);
      when(ahspStep2.variables(anyMap())).thenReturn(ahspStep1);

      var functionCaptor = ArgumentCaptor.forClass(Function.class);
      when(completeCommand.withResult(functionCaptor.capture())).thenReturn(completeCommand);

      handler.handle(jobClient, job);

      // invoke the captured withResult function
      functionCaptor.getValue().apply(resultStep);

      // capture job-level variables
      var jobVarsCaptor = ArgumentCaptor.forClass(Map.class);
      verify(completeCommand).variables(jobVarsCaptor.capture());

      // capture completion flags
      var conditionCaptor = ArgumentCaptor.forClass(Boolean.class);
      verify(ahspStep1).completionConditionFulfilled(conditionCaptor.capture());
      var cancelCaptor = ArgumentCaptor.forClass(Boolean.class);
      verify(ahspStep1).cancelRemainingInstances(cancelCaptor.capture());

      // capture element activations (may be empty)
      var activations = new ArrayList<CapturedElementActivation>();
      var ahspInvocations =
          Mockito.mockingDetails(ahspStep1).getInvocations().stream()
              .filter(inv -> inv.getMethod().getName().equals("activateElement"))
              .toList();
      if (!ahspInvocations.isEmpty()) {
        var elementIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(ahspStep1, Mockito.atLeastOnce()).activateElement(elementIdCaptor.capture());
        var elemVarsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ahspStep2, Mockito.atLeastOnce()).variables(elemVarsCaptor.capture());

        var elementIds = elementIdCaptor.getAllValues();
        var elementVars = elemVarsCaptor.getAllValues();
        for (int i = 0; i < elementIds.size(); i++) {
          activations.add(new CapturedElementActivation(elementIds.get(i), elementVars.get(i)));
        }
      }

      return new AdHocSubProcessJobResult(
          jobVarsCaptor.getValue(),
          conditionCaptor.getValue(),
          cancelCaptor.getValue(),
          activations);
    }

    public void execute(SpringConnectorJobHandler SpringConnectorJobHandler) throws Exception {
      SpringConnectorJobHandler.handle(jobClient, job);
    }
  }

  public static class JobResult {

    private final Map<String, Object> variables;
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

  public record AdHocSubProcessJobResult(
      Map<String, Object> variables,
      boolean completionConditionFulfilled,
      boolean cancelRemainingInstances,
      List<CapturedElementActivation> elementActivations) {

    public record CapturedElementActivation(String elementId, Map<String, Object> variables) {}
  }
}
