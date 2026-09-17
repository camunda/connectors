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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for security-testing-findings#275's allow-list-based remediation: {@link
 * JobHandlerContext} refuses an undeclared {@code camunda.function.type} call before Jackson ever
 * runs its typed binding, and leaves a declared one and ordinary data alone.
 */
class JobHandlerContextIntrinsicFunctionAllowListTest {

  private record TargetType(Object body) {}

  private static final String EXPLOIT_JSON =
      """
      {"body": {"camunda.function.type":"createLink",
                "params":[{"camunda.document.type":"camunda"}, "PT1H"]}}
      """;

  private ActivatedJob jobWithVariables(String variablesJson) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variablesJson);
    when(job.getTenantId()).thenReturn("tenant-1");
    when(job.getBpmnProcessId()).thenReturn("process-1");
    when(job.getPhysicalTenantId()).thenReturn(null);
    return job;
  }

  private JobHandlerContext contextWithAllowList(
      String variablesJson, IntrinsicFunctionAllowList allowList) {
    return new JobHandlerContext(
        jobWithVariables(variablesJson),
        mock(SecretProvider.class),
        mock(ValidationProvider.class),
        mock(DocumentFactory.class),
        ConnectorsObjectMapperSupplier.getCopy(),
        SecretFilter.allowAll(),
        allowList);
  }

  @Test
  void refusesADisallowedIntrinsicFunctionCallBeforeBinding() {
    var context = contextWithAllowList(EXPLOIT_JSON, IntrinsicFunctionAllowList.allowNone());

    assertThatThrownBy(() -> context.bindVariables(TargetType.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createLink");
  }

  @Test
  void allowsADeclaredIntrinsicFunctionCallPastTheGate() {
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("createLink", List.of("body"))));
    var context = contextWithAllowList(EXPLOIT_JSON, allowList);

    // This mapper (ConnectorsObjectMapperSupplier.getCopy(), with no document module registered)
    // never actually dispatches the call — that end-to-end proof, against the real production
    // mapper wiring, lives in IntrinsicFunctionAllowListEndToEndTest. What this test proves is
    // narrower and specific to this class: the allow-list gate itself does not block a declared
    // call before binding even reaches that point.
    assertThatCode(() -> context.bindVariables(TargetType.class)).doesNotThrowAnyException();
  }

  @Test
  void ordinaryVariablesWithNoIntrinsicFunctionBindNormallyEvenWithAnEmptyAllowList() {
    var context =
        contextWithAllowList("{\"body\": \"hello\"}", IntrinsicFunctionAllowList.allowNone());

    var result = context.bindVariables(TargetType.class);

    assertThat(result.body()).isEqualTo("hello");
  }
}
