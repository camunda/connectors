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

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(OutboundConnectorRuntimeConfiguration.class)
public class OutboundConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "camunda.connector.virtual-threads.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CamundaClientExecutorService camundaClientExecutorService(
      @Autowired(required = false) MeterRegistry meterRegistry) {
    ThreadFactory factory = Thread.ofVirtual().name("job-worker-virtual-", 0).factory();
    var vThreadExecutor = Executors.newThreadPerTaskExecutor(factory);
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    return new MeteredCamundaClientExecutorService(
        scheduler, true, vThreadExecutor, true, meterRegistry);
  }
}
