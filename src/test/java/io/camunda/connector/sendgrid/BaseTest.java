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
package io.camunda.connector.sendgrid;

public class BaseTest {

  protected static final String API_KEY = "test";
  protected static final String ACTUAL_API_KEY = "send_grid_key";

  protected static final String ACTUAL_SUBJECT = "subject_test";
  protected static final String ACTUAL_TYPE = "type_test";
  protected static final String ACTUAL_VALUE = "value_test";

  protected static final String ACTUAL_ID = "Test";
  protected static final String ACTUAL_DATA_VALUE = "Value";

  protected static final String FROM_EMAIL_VALUE = "from@test.com";
  protected static final String TO_EMAIL_VALUE = "receiver@test.com";

  protected static final String SENDER = "Sender";
  protected static final String RECEIVER = "Receiver";

  protected static final String SAMPLE = "Sample";
}
