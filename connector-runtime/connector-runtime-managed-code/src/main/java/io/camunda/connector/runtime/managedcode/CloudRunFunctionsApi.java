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
package io.camunda.connector.runtime.managedcode;

import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.StorageSource;
import java.time.Duration;
import java.util.Optional;

/**
 * The calls the Cloud Run functions provider makes, each allowed to an identity limited to the
 * cluster's name prefix. There is deliberately no listing: in a shared project it would reveal the
 * function names of every cluster. Failures other than the ones expressed in the return types are
 * thrown as {@link ProvisioningException}.
 */
interface CloudRunFunctionsApi extends AutoCloseable {

  /** The function, or empty when it does not exist. */
  Optional<Function> get(String functionName);

  /** Uploads the source zip through a signed URL of the location; returns where it is stored. */
  StorageSource upload(byte[] zip);

  /**
   * Starts creating the function and returns once the operation is accepted, without waiting for
   * the deployment.
   *
   * @return false when the function already exists
   */
  boolean create(String functionId, Function function);

  /** Starts deleting the function; a missing function counts as deleted. */
  void delete(String functionName);

  /** Posts the body to the function URL with an ID token of the identity. */
  HttpResult invoke(String uri, byte[] body, Duration timeout);

  @Override
  void close();

  record HttpResult(int statusCode, String body) {}
}
