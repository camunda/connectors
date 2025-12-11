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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@AutoConfigureBefore(JacksonAutoConfiguration.class)
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

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper(DocumentFactory documentFactory) {
    final ObjectMapper copy = ConnectorsObjectMapperSupplier.getCopy();
    // default intrinsic function contains a pointer of the copy
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);

    // The deserializer module contains the function executor, which contains the pointer of the
    // object mapper
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory, functionExecutor, DocumentModuleSettings.create());

    // We register the deserializer module which contains the function executor, which contains the
    // pointer of the object mapper
    // we are overloading
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }
}
