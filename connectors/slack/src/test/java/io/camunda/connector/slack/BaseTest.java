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
package io.camunda.connector.slack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class BaseTest {

  protected static final String SECRETS = "secrets.";

  protected static final String TOKEN_KEY = "token";
  protected static final String TOKEN = "sxoxp-23";
  protected static final String ACTUAL_TOKEN = "sxoxp-23984754863-2348975623103";

  protected static final String CHANNEL_KEY = "channel";
  protected static final String ACTUAL_CHANNEL = "slack";

  protected static final String TEXT_KEY = "text";
  protected static final String ACTUAL_TEXT = "some text";

  protected static final String METHOD = "chat.getMessage";
  protected static final String ACTUAL_METHOD = "chat.postMessage";

  protected Gson GSON = new GsonBuilder().create();
}
