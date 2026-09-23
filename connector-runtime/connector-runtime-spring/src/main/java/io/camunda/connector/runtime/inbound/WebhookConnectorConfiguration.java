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

import io.camunda.connector.runtime.inbound.webhook.InboundWebhookRestController;
import io.camunda.connector.runtime.inbound.webhook.WebhookAwareStandardServletMultipartResolver;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.inbound.webhook.WebhookExcludingFormContentFilter;
import io.camunda.connector.runtime.inbound.webhook.WebhookExcludingHiddenHttpMethodFilter;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;

@Configuration
@Import(InboundWebhookRestController.class)
public class WebhookConnectorConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "spring.mvc.formcontent.filter.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public FormContentFilter webhookExcludingFormContentFilter(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath) {
    return new WebhookExcludingFormContentFilter(dispatcherServletPath);
  }

  @Bean
  @ConditionalOnProperty(name = "spring.mvc.hiddenmethod.filter.enabled", havingValue = "true")
  public HiddenHttpMethodFilter webhookExcludingHiddenHttpMethodFilter(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath) {
    return new WebhookExcludingHiddenHttpMethodFilter(dispatcherServletPath);
  }

  @Bean(name = DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
  @ConditionalOnMissingBean(name = DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
  @ConditionalOnProperty(
      name = "spring.servlet.multipart.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public StandardServletMultipartResolver webhookAwareMultipartResolver(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath,
      @Value("${spring.servlet.multipart.resolve-lazily:false}") boolean resolveLazily,
      @Value("${spring.servlet.multipart.strict-servlet-compliance:false}")
          boolean strictServletCompliance) {
    var resolver = new WebhookAwareStandardServletMultipartResolver(dispatcherServletPath);
    resolver.setResolveLazily(resolveLazily);
    resolver.setStrictServletCompliance(strictServletCompliance);
    return resolver;
  }

  @Bean
  @ConditionalOnProperty(
      name = "spring.servlet.multipart.enabled",
      havingValue = "true",
      matchIfMissing = true)
  InitializingBean webhookMultipartResolverConflictCheck(
      @Qualifier(DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
          MultipartResolver multipartResolver) {
    return () -> {
      if (!(multipartResolver instanceof WebhookAwareStandardServletMultipartResolver)) {
        throw new IllegalStateException(
            "The multipartResolver bean must preserve raw non-form multipart webhook bodies, but"
                + " found "
                + multipartResolver.getClass().getName());
      }
    };
  }

  @Bean
  InitializingBean webhookFormContentFilterConflictCheck(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath,
      List<FormContentFilter> formContentFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations,
      List<ServletRegistrationBean<?>> servletRegistrations) {
    return () ->
        checkNoConflictingFilter(
            allFiltersOfType(
                formContentFilters,
                filterRegistrations,
                FormContentFilter.class,
                dispatcherServletPath,
                servletRegistrations),
            WebhookExcludingFormContentFilter.class,
            "FormContentFilter");
  }

  @Bean
  InitializingBean webhookHiddenHttpMethodFilterConflictCheck(
      @Value("${spring.mvc.servlet.path:}") String dispatcherServletPath,
      List<HiddenHttpMethodFilter> hiddenHttpMethodFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations,
      List<ServletRegistrationBean<?>> servletRegistrations) {
    return () ->
        checkNoConflictingFilter(
            allFiltersOfType(
                hiddenHttpMethodFilters,
                filterRegistrations,
                HiddenHttpMethodFilter.class,
                dispatcherServletPath,
                servletRegistrations),
            WebhookExcludingHiddenHttpMethodFilter.class,
            "HiddenHttpMethodFilter");
  }

  InitializingBean webhookFormContentFilterConflictCheck(
      String dispatcherServletPath,
      List<FormContentFilter> formContentFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations) {
    return webhookFormContentFilterConflictCheck(
        dispatcherServletPath, formContentFilters, filterRegistrations, List.of());
  }

  InitializingBean webhookHiddenHttpMethodFilterConflictCheck(
      String dispatcherServletPath,
      List<HiddenHttpMethodFilter> hiddenHttpMethodFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations) {
    return webhookHiddenHttpMethodFilterConflictCheck(
        dispatcherServletPath, hiddenHttpMethodFilters, filterRegistrations, List.of());
  }

  InitializingBean webhookFormContentFilterConflictCheck(
      List<FormContentFilter> formContentFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations) {
    return webhookFormContentFilterConflictCheck("", formContentFilters, filterRegistrations);
  }

  InitializingBean webhookHiddenHttpMethodFilterConflictCheck(
      List<HiddenHttpMethodFilter> hiddenHttpMethodFilters,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations) {
    return webhookHiddenHttpMethodFilterConflictCheck(
        "", hiddenHttpMethodFilters, filterRegistrations);
  }

  private static List<Filter> allFiltersOfType(
      List<? extends Filter> directBeans,
      List<AbstractFilterRegistrationBean<?>> filterRegistrations,
      Class<? extends Filter> type,
      String dispatcherServletPath,
      List<ServletRegistrationBean<?>> servletRegistrations) {
    Set<Filter> filtersOwnedByRegistrations = Collections.newSetFromMap(new IdentityHashMap<>());
    filterRegistrations.stream()
        .map(AbstractFilterRegistrationBean::getFilter)
        .forEach(filtersOwnedByRegistrations::add);

    var result = new ArrayList<Filter>();
    directBeans.stream()
        .filter(filter -> !filtersOwnedByRegistrations.contains(filter))
        .forEach(result::add);
    filterRegistrations.stream()
        .filter(AbstractFilterRegistrationBean::isEnabled)
        .filter(
            registration ->
                registrationCanAffectWebhookEndpoints(
                    registration, dispatcherServletPath, servletRegistrations))
        .map(AbstractFilterRegistrationBean::getFilter)
        .filter(filter -> type.isInstance(filter))
        .forEach(result::add);
    return result;
  }

  private static boolean registrationCanAffectWebhookEndpoints(
      AbstractFilterRegistrationBean<?> registration,
      String dispatcherServletPath,
      List<ServletRegistrationBean<?>> servletRegistrations) {
    if (!registration.getServletNames().isEmpty()) {
      return true;
    }
    if (registration.getUrlPatterns().isEmpty()) {
      return true;
    }

    var dispatcherServletRegistrations =
        servletRegistrations.stream()
            .filter(
                servletRegistration ->
                    servletRegistration.getServlet() instanceof DispatcherServlet)
            .toList();
    var dispatcherMappings =
        dispatcherServletRegistrations.stream()
            .flatMap(dispatcherRegistration -> dispatcherRegistration.getUrlMappings().stream())
            .toList();
    if (dispatcherServletRegistrations.stream()
            .anyMatch(dispatcherRegistration -> dispatcherRegistration.getUrlMappings().isEmpty())
        || dispatcherMappings.stream().anyMatch(mapping -> !isKnownServletMapping(mapping))) {
      return true;
    }

    var webhookPaths = new HashSet<String>();
    webhookPaths.add(normalizeServletPath(dispatcherServletPath) + "/inbound");
    dispatcherMappings.stream()
        .map(WebhookConnectorConfiguration::normalizeServletPath)
        .map(path -> path + "/inbound")
        .forEach(webhookPaths::add);
    return registration.getUrlPatterns().stream()
        .anyMatch(
            pattern ->
                webhookPaths.stream()
                    .anyMatch(webhookPath -> urlPatternCanMatchWebhook(pattern, webhookPath)));
  }

  private static boolean isKnownServletMapping(String mapping) {
    if (mapping == null || mapping.isBlank() || mapping.equals("/") || mapping.equals("/*")) {
      return true;
    }
    int wildcardIndex = mapping.indexOf('*');
    return mapping.startsWith("/")
        && (wildcardIndex == -1
            || (mapping.endsWith("/*") && wildcardIndex == mapping.length() - 1));
  }

  private static boolean urlPatternCanMatchWebhook(String pattern, String webhookPath) {
    if (pattern == null || pattern.isBlank() || pattern.equals("/") || pattern.equals("/*")) {
      return true;
    }
    if (pattern.startsWith("*.")) {
      return true;
    }
    if (pattern.endsWith("/*")) {
      String prefix = pattern.substring(0, pattern.length() - 2);
      return webhookPath.equals(prefix)
          || webhookPath.startsWith(prefix + "/")
          || prefix.startsWith(webhookPath + "/");
    }
    return pattern.equals(webhookPath) || pattern.startsWith(webhookPath + "/");
  }

  private static String normalizeServletPath(String servletPath) {
    if (servletPath == null || servletPath.isBlank() || servletPath.equals("/")) {
      return "";
    }
    String normalized = servletPath;
    if (normalized.endsWith("/*")) {
      normalized = normalized.substring(0, normalized.length() - 2);
    } else if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
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

  @Bean
  public WebhookConnectorRegistry webhookConnectorRegistry() {
    return new WebhookConnectorRegistry();
  }
}
