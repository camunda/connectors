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
package io.camunda.connector.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an outbound connector and configures meta-data. */
@Target(value = {ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OutboundConnector {

  /** Name of the connector */
  String name();

  /** Input variables the connector reads */
  String[] inputVariables() default {};

  /** Job / task type the connector registers for */
  String type();

  /**
   * Whether to activate jobs for this connector with a lease, fencing complete/fail/throw-error
   * commands against a stale, superseded activation of the same job.
   *
   * <p>This is a request, not a guarantee, and version-skew behavior during a rolling upgrade
   * differs by transport: over gRPC, a broker or gateway that predates job leasing drops the
   * unknown field and activates the job without a lease token; over REST, an older gateway instead
   * rejects the activation request outright (HTTP 400). Connector code that depends on fencing for
   * correctness must check {@link io.camunda.connector.api.outbound.JobContext#getLeaseToken()} for
   * {@code null} (the gRPC case) and handle activation failures (the REST case), rather than
   * assuming a lease token is always present just because this flag is {@code true}.
   */
  boolean withLease() default false;
}
