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
package io.camunda.connector.impl.config;

/**
 * Helper to resolve configuration properties of the application. It will either use a delegate set
 * by calling setCustomConfigurationPropertyResolver or default to System.getEnv().
 */
public class ConnectorConfigurationUtil {

  private static ConnectorPropertyResolver propertyResolverDelegate;

  public static void setCustomPropertyResolver(ConnectorPropertyResolver propertyResolver) {
    propertyResolverDelegate = propertyResolver;
  }

  public static boolean containsProperty(String key) {
    if (propertyResolverDelegate != null) {
      return propertyResolverDelegate.containsProperty(key);
    }
    return System.getenv().containsKey(key);
  }

  public static String getProperty(String key) {
    if (propertyResolverDelegate != null) {
      return propertyResolverDelegate.getProperty(key);
    }
    return System.getenv(key);
  }

  public static String getProperty(String key, String defaultValue) {
    String result = getProperty(key);
    if (result == null) {
      return defaultValue;
    } else {
      return result;
    }
  }

}
