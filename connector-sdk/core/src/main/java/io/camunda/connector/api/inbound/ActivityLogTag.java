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
package io.camunda.connector.api.inbound;

/**
 * Tags for activities in the inbound connector context.
 *
 * <p>These tags are used to categorize and identify different types of activities in the activity
 * log. They help in understanding the context of the logged activities.
 */
public class ActivityLogTag {

  public static final String MESSAGE = "Message";
  public static final String CONSUMER = "Consumer";
  public static final String CORRELATION = "Correlation";
  public static final String LIFECYCLE = "Lifecycle";
  public static final String QUEUEING = "Queueing";
  public static final String ACTIVATION = "Activation";
}
