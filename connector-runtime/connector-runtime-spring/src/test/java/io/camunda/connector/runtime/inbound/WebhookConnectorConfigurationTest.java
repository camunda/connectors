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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.inbound.webhook.WebhookExcludingFormContentFilter;
import io.camunda.connector.runtime.inbound.webhook.WebhookExcludingHiddenHttpMethodFilter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.filter.HiddenHttpMethodFilter;

/**
 * Exercises the explicit-vs-inferred resolution of {@code
 * camunda.connector.webhook.append-physical-tenant-and-tenant-to-path} in {@link
 * WebhookConnectorConfiguration#webhookConnectorRegistry}, called directly as a plain method
 * (bypassing the Spring context) the same way {@link PhysicalTenantIdResolutionTest} exercises
 * {@link InboundConnectorRuntimeConfiguration}.
 */
class WebhookConnectorConfigurationTest {

  private final WebhookConnectorConfiguration configuration = new WebhookConnectorConfiguration();

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CamundaClientRegistry> providerFor(CamundaClientRegistry registry) {
    var provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(registry);
    return provider;
  }

  @Test
  void explicitTrueWinsRegardlessOfClientCount() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));

    var result = configuration.webhookConnectorRegistry(true, providerFor(registry));

    assertThat(result.appendsPhysicalTenantAndTenantToPath()).isTrue();
  }

  @Test
  void explicitFalseWinsEvenWithMultipleClients() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));

    var result = configuration.webhookConnectorRegistry(false, providerFor(registry));

    assertThat(result.appendsPhysicalTenantAndTenantToPath()).isFalse();
  }

  @Test
  void unsetWithSingleClientInfersFalse() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("default"));

    var result = configuration.webhookConnectorRegistry(null, providerFor(registry));

    assertThat(result.appendsPhysicalTenantAndTenantToPath()).isFalse();
  }

  @Test
  void unsetWithMultipleClientsInfersTrue() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));

    var result = configuration.webhookConnectorRegistry(null, providerFor(registry));

    assertThat(result.appendsPhysicalTenantAndTenantToPath()).isTrue();
  }

  @Test
  void unsetWithNoRegistryAvailableInfersFalse() {
    var result = configuration.webhookConnectorRegistry(null, providerFor(null));

    assertThat(result.appendsPhysicalTenantAndTenantToPath()).isFalse();
  }

  @Test
  void formContentFilterConflictCheckFailsFastOnIncompatibleCustomFilter() {
    var check =
        configuration.webhookFormContentFilterConflictCheck(
            List.of(new FormContentFilter(), new WebhookExcludingFormContentFilter("")), List.of());

    assertThatThrownBy(check::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FormContentFilter");
  }

  @Test
  void formContentFilterConflictCheckFailsFastOnIncompatibleFilterRegistrationBean() {
    // The custom filter is never exposed as a plain FormContentFilter bean at all here -- only
    // wrapped in a FilterRegistrationBean, which is how it would actually be registered.
    var check =
        configuration.webhookFormContentFilterConflictCheck(
            List.of(new WebhookExcludingFormContentFilter("")),
            List.<AbstractFilterRegistrationBean<?>>of(
                new FilterRegistrationBean<>(new FormContentFilter())));

    assertThatThrownBy(check::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FormContentFilter");
  }

  @Test
  void formContentFilterConflictCheckPassesWithOnlyTheWebhookExcludingFilter() {
    var check =
        configuration.webhookFormContentFilterConflictCheck(
            List.of(new WebhookExcludingFormContentFilter("")), List.of());

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void formContentFilterConflictCheckIgnoresUnrelatedFilterRegistrationBeans() {
    var check =
        configuration.webhookFormContentFilterConflictCheck(
            List.of(new WebhookExcludingFormContentFilter("")),
            List.<AbstractFilterRegistrationBean<?>>of(
                new FilterRegistrationBean<>(new HiddenHttpMethodFilter())));

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void formContentFilterConflictCheckIgnoresDisabledFilterRegistrationBean() {
    // Spring Boot never installs a disabled registration bean in the servlet container, so it
    // poses no risk regardless of what filter it wraps.
    var registration = new FilterRegistrationBean<>(new FormContentFilter());
    registration.setEnabled(false);
    var check =
        configuration.webhookFormContentFilterConflictCheck(
            List.of(new WebhookExcludingFormContentFilter("")),
            List.<AbstractFilterRegistrationBean<?>>of(registration));

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void formContentFilterConflictCheckPassesWithNoFiltersAtAll() {
    // Matches spring.mvc.formcontent.filter.enabled=false: no FormContentFilter bean at all.
    var check = configuration.webhookFormContentFilterConflictCheck(List.of(), List.of());

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void hiddenHttpMethodFilterConflictCheckFailsFastOnIncompatibleCustomFilter() {
    var check =
        configuration.webhookHiddenHttpMethodFilterConflictCheck(
            List.of(new HiddenHttpMethodFilter(), new WebhookExcludingHiddenHttpMethodFilter("")),
            List.of());

    assertThatThrownBy(check::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HiddenHttpMethodFilter");
  }

  @Test
  void hiddenHttpMethodFilterConflictCheckFailsFastOnIncompatibleFilterRegistrationBean() {
    var check =
        configuration.webhookHiddenHttpMethodFilterConflictCheck(
            List.of(new WebhookExcludingHiddenHttpMethodFilter("")),
            List.<AbstractFilterRegistrationBean<?>>of(
                new FilterRegistrationBean<>(new HiddenHttpMethodFilter())));

    assertThatThrownBy(check::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HiddenHttpMethodFilter");
  }

  @Test
  void hiddenHttpMethodFilterConflictCheckIgnoresDisabledFilterRegistrationBean() {
    var registration = new FilterRegistrationBean<>(new HiddenHttpMethodFilter());
    registration.setEnabled(false);
    var check =
        configuration.webhookHiddenHttpMethodFilterConflictCheck(
            List.of(new WebhookExcludingHiddenHttpMethodFilter("")),
            List.<AbstractFilterRegistrationBean<?>>of(registration));

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void hiddenHttpMethodFilterConflictCheckPassesWithOnlyTheWebhookExcludingFilter() {
    var check =
        configuration.webhookHiddenHttpMethodFilterConflictCheck(
            List.of(new WebhookExcludingHiddenHttpMethodFilter("")), List.of());

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void hiddenHttpMethodFilterConflictCheckPassesWithNoFiltersAtAll() {
    // Matches the default spring.mvc.hiddenmethod.filter.enabled=false: no bean registered.
    var check = configuration.webhookHiddenHttpMethodFilterConflictCheck(List.of(), List.of());

    assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
  }
}
