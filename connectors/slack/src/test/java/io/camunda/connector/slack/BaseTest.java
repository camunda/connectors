/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
