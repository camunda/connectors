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
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.inbound.WebhookConnectorConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@AutoConfiguration
@AutoConfigureBefore(InboundConnectorsAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "camunda.connector.webhook",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(WebhookConnectorConfiguration.class)
public class WebhookConnectorAutoConfiguration {
  // TODO: Remove this with the Migration to Jackson 3
  // This is currently required so that Webhook Endpoint responses are correctly
  // serialized to JSON (e.g. including document support)
  @Bean
  @ConditionalOnMissingBean
  public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter(
      @ConnectorsObjectMapper ObjectMapper connectorsMapper) {
    return new MappingJackson2HttpMessageConverter(connectorsMapper);
  }
}
