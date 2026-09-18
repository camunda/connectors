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
import java.util.List;
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

  /**
   * Replaces Spring Boot's auto-configured {@code FormContentFilter} (Spring Boot backs off once a
   * bean of that type already exists) with one that skips {@code /inbound/**}. See {@link
   * WebhookExcludingFormContentFilter} for why.
   *
   * <p>Gated by the same property Spring Boot's own auto-configured filter is, so an operator who
   * explicitly disabled form-content parsing entirely ({@code
   * spring.mvc.formcontent.filter.enabled=false}) keeps that setting effective instead of this bean
   * reinstalling the filter (just narrower) for every non-webhook endpoint.
   */
  @Bean
  @ConditionalOnProperty(
      name = "spring.mvc.formcontent.filter.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FormContentFilter formContentFilter(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath) {
    return new WebhookExcludingFormContentFilter(dispatcherServletPath);
  }

  /**
   * Replaces Spring Boot's auto-configured {@code HiddenHttpMethodFilter} the same way {@link
   * #formContentFilter} replaces its {@code FormContentFilter}, for the same reason: see {@link
   * WebhookExcludingHiddenHttpMethodFilter}. Gated by the same property Spring Boot's own
   * auto-configured filter is (off by default, unlike {@code FormContentFilter}'s), so this bean
   * only exists when an operator has actually opted into hidden-method overriding.
   */
  @Bean
  @ConditionalOnProperty(name = "spring.mvc.hiddenmethod.filter.enabled", havingValue = "true")
  public HiddenHttpMethodFilter hiddenHttpMethodFilter(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath) {
    return new WebhookExcludingHiddenHttpMethodFilter(dispatcherServletPath);
  }

  /**
   * Fails startup with a clear error if some other {@code FormContentFilter} filter is also
   * registered (e.g. a downstream application defining its own under a different bean name, or
   * wrapping one in a {@code FilterRegistrationBean} instead of exposing it as a plain bean): that
   * filter has no reason to know about {@code /inbound/**} and would still fully buffer webhook
   * PUT/DELETE bodies before this fix's guards run, silently defeating it. Rather than fail open
   * (letting an unrelated filter quietly reintroduce the vulnerability) or fail closed on a
   * bean-name collision only, this checks every {@code FormContentFilter}, by type, found either as
   * a plain bean or wrapped in a registration bean.
   */
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

  /**
   * Same conflict check as {@link #webhookFormContentFilterConflictCheck}, for {@code
   * HiddenHttpMethodFilter} instead: see {@link WebhookExcludingHiddenHttpMethodFilter} for why an
   * unrelated one is just as much of a bypass.
   */
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

  /**
   * {@code FilterRegistrationBean} (and its siblings, e.g. {@code
   * DelegatingFilterProxyRegistrationBean}) registers a wrapped filter directly with the servlet
   * container; the bean visible to Spring is the registration bean itself, of type {@code
   * AbstractFilterRegistrationBean}, never the wrapped filter's own type. A {@code
   * List<FormContentFilter>} injection point (as used by {@link
   * #webhookFormContentFilterConflictCheck}) therefore silently misses a {@code FormContentFilter}
   * registered this way -- so those must be unwrapped via {@code getFilter()} and included
   * explicitly.
   *
   * <p>A registration with {@code setEnabled(false)} is skipped: Spring Boot never installs it in
   * the servlet container, so it poses no risk regardless of what it wraps.
   */
  private static List<Filter> allFiltersOfType(
      List<? extends Filter> directBeans,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations,
      Class<? extends Filter> type) {
    var result = new ArrayList<Filter>(directBeans);
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
              + " defeating this security fix. Either remove the custom filter or have it"
              + " extend "
              + webhookAwareType.getSimpleName()
              + " (or otherwise skip /inbound/** itself).");
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
