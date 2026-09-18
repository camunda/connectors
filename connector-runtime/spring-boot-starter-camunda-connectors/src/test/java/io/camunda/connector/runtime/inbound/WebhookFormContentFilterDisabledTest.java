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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.filter.FormContentFilter;

/**
 * Regression test for a review finding on PR #9012: {@code WebhookConnectorConfiguration}'s {@code
 * formContentFilter} bean must respect {@code spring.mvc.formcontent.filter.enabled=false} the same
 * way Spring Boot's own auto-configured filter does, rather than unconditionally reinstalling
 * form-content parsing (narrowed to skip {@code /inbound/**}) for every other endpoint when an
 * operator has explicitly disabled it.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
      "spring.mvc.formcontent.filter.enabled=false",
    })
class WebhookFormContentFilterDisabledTest {

  @MockitoBean private CamundaClient camundaClient;

  @MockitoBean private OutboundConnectorFactory outboundConnectorFactory;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void shouldNotRegisterAnyFormContentFilterWhenExplicitlyDisabled() {
    assertThat(applicationContext.getBeanNamesForType(FormContentFilter.class)).isEmpty();
  }
}
