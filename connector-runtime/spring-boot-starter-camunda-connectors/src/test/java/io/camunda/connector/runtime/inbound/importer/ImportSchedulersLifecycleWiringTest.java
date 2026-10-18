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
package io.camunda.connector.runtime.inbound.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.event.CamundaClientClosingSpringEvent;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Pins that {@link ImportSchedulers} actually receives {@code CamundaClient} lifecycle callbacks in
 * a real context — not merely that its {@code onStart}/{@code onStop} methods behave correctly when
 * called directly, which {@code ImportSchedulersTest} covers.
 *
 * <p>This is worth its own context test because {@code AnnotationProcessorConfiguration} injects a
 * plain {@code Set<CamundaClientLifecycleAware>} into {@code CamundaClientEventListener} at
 * construction time, while {@link ImportSchedulers} is contributed by a different,
 * {@code @ConditionalOnProperty}-gated {@code @Configuration}. Were the listener built before this
 * bean were eligible for that set, the lifecycle handling would silently never run and every direct
 * unit test would still pass.
 *
 * <p>Polling itself is pushed past the end of the test via a long initial delay: the scheduled
 * ticks are irrelevant here, only the registration is.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.connector.polling.enabled=true",
      "camunda.connector.polling.initial-delay=600000",
      "camunda.connector.webhook.enabled=false"
    })
class ImportSchedulersLifecycleWiringTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private ImportSchedulers importSchedulers;

  @Autowired private CamundaClient camundaClient;

  @Test
  void clientLifecycleEventsReachTheImportSchedulers() {
    assertThat(importSchedulers.activePhysicalTenantIds()).containsExactly("default");

    applicationContext.publishEvent(new CamundaClientClosingSpringEvent(this, camundaClient));
    assertThat(importSchedulers.activePhysicalTenantIds()).isEmpty();

    applicationContext.publishEvent(new CamundaClientCreatedSpringEvent(this, camundaClient));
    assertThat(importSchedulers.activePhysicalTenantIds()).containsExactly("default");
  }
}
