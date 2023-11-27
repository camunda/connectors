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

import io.camunda.connector.runtime.inbound.InboundConnectorRuntimeConfiguration;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.auth.AuthInterface;
import io.camunda.operate.auth.JwtAuthentication;
import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.spring.client.CamundaAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.OperateClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.properties.OperateClientConfigurationProperties;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@AutoConfigureAfter({CamundaAutoConfiguration.class, OutboundConnectorsAutoConfiguration.class})
@ConditionalOnProperty(
    prefix = "camunda.connector.polling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(InboundConnectorRuntimeConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(OperateClientConfigurationProperties.class)
public class InboundConnectorsAutoConfiguration {

  @Value("${operate.token.audience:operate}")
  private String operateAudience;

  @Bean
  @ConditionalOnMissingBean
  public CamundaOperateClient myOperateClient(
      OperateClientProdAutoConfiguration configuration,
      OperateClientConfigurationProperties properties,
      Identity identity) {
    return configuration.camundaOperateClient(operatePropertiesProxy(properties, identity));
  }

  @Bean
  @ConditionalOnMissingBean
  public OperateClientProdAutoConfiguration operateClientProdAutoConfiguration() {
    return new OperateClientProdAutoConfiguration();
  }

  private OperateClientConfigurationProperties operatePropertiesProxy(
      OperateClientConfigurationProperties properties,
      Identity identity) {
    return (OperateClientConfigurationProperties) Proxy.newProxyInstance(
        OperateClientConfigurationProperties.class.getClassLoader(),
        new Class[] {OperateClientConfigurationProperties.class},
        (proxy, method, args) -> {
          try {
            if (method.getReturnType().equals(AuthInterface.class)
                && identity.authentication().isAvailable()) {
              return new IdentityAuth(identity, operateAudience);
            }

            return method.invoke(properties, args);
          } catch (final InvocationTargetException e) {
            throw e.getCause();
          }
        });
  }

  private static class IdentityAuth extends JwtAuthentication {
    final private Identity identity;
    final private String audience;

    public IdentityAuth(Identity identity, String audience) {
      this.identity = identity;
      this.audience = audience;
    }

    @Override
    public void authenticate(CamundaOperateClient camundaOperateClient) throws OperateException {
      Tokens tokens = identity.authentication().requestToken(audience);
      this.setToken(camundaOperateClient, tokens.getAccessToken());
    }
  }
}
