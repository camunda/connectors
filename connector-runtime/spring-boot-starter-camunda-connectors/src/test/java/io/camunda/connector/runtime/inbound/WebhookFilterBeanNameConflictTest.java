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

import io.camunda.connector.runtime.WebhookConnectorAutoConfiguration;
import io.camunda.connector.runtime.inbound.webhook.WebhookAwareStandardServletMultipartResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.servlet.DispatcherServlet;

class WebhookFilterBeanNameConflictTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withAllowBeanDefinitionOverriding(false)
          .withConfiguration(AutoConfigurations.of(WebhookConnectorAutoConfiguration.class));

  @Test
  void customFormContentFilterReachesSecurityConflictCheck() {
    contextRunner
        .withUserConfiguration(CustomFormContentFilterConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .rootCause()
                  .hasMessageContaining("Found FormContentFilter(s)")
                  .hasMessageContaining("WebhookExcludingFormContentFilter");
            });
  }

  @Test
  void customHiddenHttpMethodFilterReachesSecurityConflictCheck() {
    contextRunner
        .withPropertyValues("spring.mvc.hiddenmethod.filter.enabled=true")
        .withUserConfiguration(CustomHiddenHttpMethodFilterConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .rootCause()
                  .hasMessageContaining("Found HiddenHttpMethodFilter(s)")
                  .hasMessageContaining("WebhookExcludingHiddenHttpMethodFilter");
            });
  }

  @Test
  void mappedFormContentFilterReachesSecurityConflictCheckWithCustomDispatcherPath() {
    contextRunner
        .withUserConfiguration(CustomMappedFormContentFilterConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .rootCause()
                  .hasMessageContaining("Found FormContentFilter(s)")
                  .hasMessageContaining("WebhookExcludingFormContentFilter");
            });
  }

  @Test
  void defaultMultipartResolverIsWebhookAware() {
    contextRunner.run(
        context ->
            assertThat(context)
                .hasSingleBean(WebhookAwareStandardServletMultipartResolver.class)
                .hasBean("multipartResolver"));
  }

  @Test
  void multipartResolverIsNotDeclaredWhenMultipartIsDisabled() {
    contextRunner
        .withPropertyValues("spring.servlet.multipart.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("multipartResolver"));
  }

  @Test
  void multipartResolverPreservesStrictServletCompliance() {
    contextRunner
        .withPropertyValues("spring.servlet.multipart.strict-servlet-compliance=true")
        .run(
            context -> {
              var resolver = context.getBean(WebhookAwareStandardServletMultipartResolver.class);
              var request = new MockHttpServletRequest();
              request.setRequestURI("/management/import");
              request.setContentType("multipart/related; boundary=x");

              assertThat(resolver.isMultipart(request)).isFalse();
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomFormContentFilterConfiguration {

    @Bean
    FormContentFilter formContentFilter() {
      return new FormContentFilter();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomHiddenHttpMethodFilterConfiguration {

    @Bean
    HiddenHttpMethodFilter hiddenHttpMethodFilter() {
      return new HiddenHttpMethodFilter();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomMappedFormContentFilterConfiguration {

    @Bean
    DispatcherServlet dispatcherServlet() {
      return new DispatcherServlet();
    }

    @Bean(name = "dispatcherServletRegistration")
    DispatcherServletRegistrationBean dispatcherServletRegistration(
        DispatcherServlet dispatcherServlet) {
      return new DispatcherServletRegistrationBean(dispatcherServlet, "/api");
    }

    @Bean
    FilterRegistrationBean<FormContentFilter> mappedFormContentFilter() {
      var registration = new FilterRegistrationBean<>(new FormContentFilter());
      registration.addUrlPatterns("/api/inbound/*");
      return registration;
    }
  }
}
