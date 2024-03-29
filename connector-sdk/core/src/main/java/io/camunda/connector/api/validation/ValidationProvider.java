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

package io.camunda.connector.api.validation;

/**
 * Provider of validation for an environment. This class will be instantiated from an environment
 * runtime according to the <a
 * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html">Service
 * Provider Interface (SPI)</a> documentation.
 */
public interface ValidationProvider {
  /**
   * Performs a validation on the given object and throws an exception if the object is invalid.
   *
   * @param objectToValidate the object the validation should run for
   */
  void validate(Object objectToValidate);
}
