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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobHandlerContextTest {

  @Mock private ActivatedJob activatedJob;
  @Mock private SecretProvider secretProvider;
  @Mock private ObjectMapper objectMapper;

  @Mock private ValidationProvider validationProvider;

  @InjectMocks private JobHandlerContext jobHandlerContext;

  @Test
  void getVariablesAsType() throws JsonProcessingException {
    Class<Integer> integerClass = Integer.class;
    when(activatedJob.getVariables()).thenReturn("");
    jobHandlerContext.bindVariables(integerClass);
    verify(objectMapper).readValue("", Integer.class);
  }

  @Test
  void getVariables() {
    jobHandlerContext.getVariables();
    verify(activatedJob).getVariables();
  }
}
