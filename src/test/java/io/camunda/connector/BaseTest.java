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
package io.camunda.connector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class BaseTest {

  protected static final String ACTUAL_QUEUE_URL = "https://sqs.region.amazonaws.com/camunda-test";
  protected static final String ACTUAL_QUEUE_REGION = "ap-region-1";
  protected static final String SECRETS = "secrets.";
  protected static final String ACCESS_KEY = "AWS_ACCESS";
  protected static final String SECRET_KEY = "AWS_SECRET";
  protected static final String QUEUE_URL_KEY = "QUEUE_URL";
  protected static final String QUEUE_REGION_KEY = "QUEUE_REGION";
  protected static final String ACTUAL_ACCESS_KEY = "access_key 123456";
  protected static final String ACTUAL_SECRET_KEY = "secret_key 654321";
  protected static final String SQS_MESSAGE_BODY = "{\"myKey\":\"myVal\"}";
  protected static final String WRONG_MESSAGE_BODY = "its wrong msg";
  protected static final String MSG_ID = "nmsgId";

  protected static final Gson GSON = new GsonBuilder().create();
}
