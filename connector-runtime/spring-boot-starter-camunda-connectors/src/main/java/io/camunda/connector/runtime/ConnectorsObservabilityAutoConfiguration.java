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

import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImporter;
import io.camunda.connector.runtime.metrics.ContextAwareLogbackMetrics;
import io.camunda.zeebe.client.ZeebeClient;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.LogbackMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(LogbackMetricsAutoConfiguration.class)
public class ConnectorsObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
  public LogbackMetrics logbackMetrics() {
    return new ContextAwareLogbackMetrics();
  }

  @Bean(name = "zeebeClientHealthIndicator") // overrides the health indicator from Spring Zeebe
  public ZeebeHealthIndicator zeebeClientHealthIndicator(ZeebeClient zeebeClient) {
    return new ZeebeHealthIndicator(zeebeClient);
  }

  @Bean
  public OperateHealthIndicator operateHealthIndicator(
      @Autowired(required = false) ProcessDefinitionImporter processDefinitionImporter) {
    return new OperateHealthIndicator(processDefinitionImporter);
  }
}
