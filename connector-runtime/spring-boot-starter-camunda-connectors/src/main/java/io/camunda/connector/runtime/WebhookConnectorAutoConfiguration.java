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

import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.inbound.WebhookConnectorConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@AutoConfiguration
@AutoConfigureBefore({InboundConnectorsAutoConfiguration.class, WebMvcAutoConfiguration.class})
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
  //
  // Spring MVC uses exactly one MappingJackson2HttpMessageConverter for every application/json
  // @RequestBody/@ResponseBody in the whole app, not just the webhook controller (which reads its
  // own inbound payload as raw bytes and never goes through this converter at all). The
  // @ConnectorsObjectMapper this bean used to inject also carries live camunda.function.type
  // dispatch (DefaultIntrinsicFunctionExecutor) with no allow-list gate of its own. Its other
  // consumers each bind a specific, known-shape payload from a context where that's already
  // accounted for; an app-wide HTTP converter is different; it accepts an arbitrary request body
  // for ANY current or future @RequestBody-bound field, with no way to know in general whether
  // whatever consumes that field guards against live dispatch. Reusing it here let ANY
  // @RequestBody binding in the app (e.g. ConfigurationValidationRestController's credentialRef, a
  // plain String field) dispatch an arbitrary registered intrinsic function during JSON binding,
  // before any application code -- let alone an allow-list check -- ever ran
  // (security-testing-findings#275's exact bypass, on a third path). This bean only ever needed
  // document *serialization* for response bodies, so it builds its own mapper with just that,
  // rather than reusing a mapper built for a different, allow-list-protected purpose.
  @Bean
  @ConditionalOnMissingBean
  public MappingJackson2HttpMessageConverter jackson2HttpMessageConverter() {
    var mapper =
        ConnectorsObjectMapperSupplier.getCopy()
            .registerModule(new JacksonModuleDocumentSerializer());
    return new MappingJackson2HttpMessageConverter(mapper);
  }
}
