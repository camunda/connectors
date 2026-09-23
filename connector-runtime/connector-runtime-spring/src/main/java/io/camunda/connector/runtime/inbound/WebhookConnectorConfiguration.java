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

import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.inbound.webhook.InboundWebhookRestController;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.inbound.webhook.WebhookExcludingFormContentFilter;
import io.camunda.connector.runtime.inbound.webhook.WebhookExcludingHiddenHttpMethodFilter;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.filter.HiddenHttpMethodFilter;

@Configuration
@Import(InboundWebhookRestController.class)
public class WebhookConnectorConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "spring.mvc.formcontent.filter.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FormContentFilter formContentFilter(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath) {
    return new WebhookExcludingFormContentFilter(dispatcherServletPath);
  }

  @Bean
  @ConditionalOnProperty(name = "spring.mvc.hiddenmethod.filter.enabled", havingValue = "true")
  public HiddenHttpMethodFilter hiddenHttpMethodFilter(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath) {
    return new WebhookExcludingHiddenHttpMethodFilter(dispatcherServletPath);
  }

  @Bean
  InitializingBean webhookFormContentFilterConflictCheck(
      List<FormContentFilter> formContentFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations) {
    return () ->
        checkNoConflictingFilter(
            allFiltersOfType(formContentFilters, filterRegistrations, FormContentFilter.class),
            WebhookExcludingFormContentFilter.class,
            "FormContentFilter");
  }

  @Bean
  InitializingBean webhookHiddenHttpMethodFilterConflictCheck(
      List<HiddenHttpMethodFilter> hiddenHttpMethodFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations) {
    return () ->
        checkNoConflictingFilter(
            allFiltersOfType(
                hiddenHttpMethodFilters, filterRegistrations, HiddenHttpMethodFilter.class),
            WebhookExcludingHiddenHttpMethodFilter.class,
            "HiddenHttpMethodFilter");
  }

  private static List<Filter> allFiltersOfType(
      List<? extends Filter> directBeans,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations,
      Class<? extends Filter> type) {
    Set<Filter> filtersOwnedByDisabledRegistrations =
        Collections.newSetFromMap(new IdentityHashMap<>());
    filterRegistrations.stream()
        .filter(registration -> !registration.isEnabled())
        .map(AbstractFilterRegistrationBean::getFilter)
        .forEach(filtersOwnedByDisabledRegistrations::add);

    var result = new ArrayList<Filter>();
    directBeans.stream()
        .filter(filter -> !filtersOwnedByDisabledRegistrations.contains(filter))
        .forEach(result::add);
    filterRegistrations.stream()
        .filter(AbstractFilterRegistrationBean::isEnabled)
        .map(AbstractFilterRegistrationBean::getFilter)
        .filter(filter -> type.isInstance(filter))
        .forEach(result::add);
    return result;
  }

  private static void checkNoConflictingFilter(
      List<Filter> filters, Class<?> webhookAwareType, String filterName) {
    var incompatible =
        filters.stream()
            .filter(f -> !webhookAwareType.isInstance(f))
            .map(f -> f.getClass().getName())
            .toList();
    if (!incompatible.isEmpty()) {
      throw new IllegalStateException(
          "Found "
              + filterName
              + "(s) that are not "
              + webhookAwareType.getSimpleName()
              + ": "
              + incompatible
              + ". Such a filter still runs before path resolution, the webhook rate limit and"
              + " the body-size guard, and would fully buffer PUT/DELETE/POST"
              + " application/x-www-form-urlencoded bodies to /inbound/** with no size limit,"
              + " defeating this security fix. This check only recognizes "
              + webhookAwareType.getSimpleName()
              + " itself (or a subclass of it) as webhook-aware, regardless of how another"
              + " filter implements its own exclusion, so remove the custom filter or have it"
              + " extend "
              + webhookAwareType.getSimpleName()
              + " instead.");
    }
  }

  /**
   * When the property is unset, infers the flag from the configured client count: multiple {@code
   * camunda.clients.*} entries mean webhook paths are ambiguous across engines unless scoped, so
   * scoping is enabled automatically. A single client (the common case today) keeps the legacy
   * unscoped path. An explicit property value always wins over this inference.
   */
  @Bean
  public WebhookConnectorRegistry webhookConnectorRegistry(
      @Value("${camunda.connector.webhook.append-physical-tenant-and-tenant-to-path:#{null}}")
          Boolean explicitAppendPhysicalTenantAndTenantToPath,
      ObjectProvider<CamundaClientRegistry> clientRegistryProvider) {
    if (explicitAppendPhysicalTenantAndTenantToPath != null) {
      return new WebhookConnectorRegistry(explicitAppendPhysicalTenantAndTenantToPath);
    }
    var clientRegistry = clientRegistryProvider.getIfAvailable();
    boolean multipleClients = clientRegistry != null && clientRegistry.clientNames().size() > 1;
    return new WebhookConnectorRegistry(multipleClients);
  }
}
