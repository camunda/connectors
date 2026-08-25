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
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivatedJobContextTest {

  @Mock private ActivatedJob activatedJob;

  private ActivatedJobContext context() {
    return new ActivatedJobContext(activatedJob, () -> "{}");
  }

  @Test
  void physicalTenantId_isTakenFromTheJob() {
    when(activatedJob.getPhysicalTenantId()).thenReturn("engine-1");

    assertThat(context().getPhysicalTenantId()).isEqualTo("engine-1");
  }

  @Test
  void physicalTenantId_isNullWhenTheJobReportsNone() {
    // clusters that predate multi-engine support report an empty physical tenant
    when(activatedJob.getPhysicalTenantId()).thenReturn("");

    assertThat(context().getPhysicalTenantId()).isNull();
  }
}
