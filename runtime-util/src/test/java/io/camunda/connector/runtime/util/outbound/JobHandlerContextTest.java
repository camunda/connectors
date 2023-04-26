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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobHandlerContextTest {

  @Mock private ActivatedJob activatedJob;
  @Mock private SecretProvider secretProvider;

  @InjectMocks private JobHandlerContext jobHandlerContext;

  @Test
  void getVariablesAsType() {
    Class<Integer> integerClass = Integer.class;
    jobHandlerContext.getVariablesAsType(integerClass);
    verify(activatedJob).getVariablesAsType(integerClass);
  }

  @Test
  void getVariables() {
    jobHandlerContext.getVariables();
    verify(activatedJob).getVariables();
  }

  @Test
  void getCustomHeaders_HappyCase() {
    when(activatedJob.getCustomHeaders()).thenReturn(Map.of("headerKey", "headerVal"));
    Map<String, String> headers = jobHandlerContext.getCustomHeaders();
    assertThat(headers).isNotNull().hasSize(1);
  }

  @Test
  void getCustomHeaders_NoCustomHeaders_ReturnEmptyMap() {
    when(activatedJob.getCustomHeaders()).thenReturn(null);
    Map<String, String> headers = jobHandlerContext.getCustomHeaders();
    verify(activatedJob).getCustomHeaders();
    assertThat(headers).isNotNull().isEmpty();
  }

  @Test
  void asJson() {
    jobHandlerContext.asJson();
    verify(activatedJob).toJson();
  }
}
