/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import com.google.gson.Gson;
import io.camunda.connector.suppliers.SnsGsonComponentSupplier;

public abstract class BaseTest {

  protected static final String AWS_TOPIC_REGION = "AWS_TOPIC_REGION";
  protected static final String ACTUAL_TOPIC_REGION = "us-east-1";
  protected static final String SECRETS = "secrets.";
  protected static final String AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
  protected static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
  protected static final String ACTUAL_ACCESS_KEY = "4W553CR3TK3Y";
  protected static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  protected static final String AWS_TOPIC_ARN = "AWS_TOPIC_ARN";
  protected static final String ACTUAL_TOPIC_ARN = "arn:aws:sns:us-east-1:036433529947:test";
  protected static final String SNS_MESSAGE_BODY = "test message";
  protected static final String WRONG_MESSAGE_BODY = "test wrong message";
  protected static final String MSG_ID = "f3f7ac8f-2ff8-48a0-bb30-f220654f6a5f";

  protected static final String DEFAULT_REQUEST_BODY =
      "{\n"
          + "  \"authentication\":{\n"
          + "    \"secretKey\":\"abc\",\n"
          + "    \"accessKey\":\"def\"\n"
          + "  },\n"
          + "  \"topic\":{\n"
          + "    \"message\":\"MyMessage\",\n"
          + "    \"messageAttributes\":{\n"
          + "      \"attribute2\":{\n"
          + "        \"StringValue\":\"attribute 2 value\",\n"
          + "        \"DataType\":\"String\"\n"
          + "      },\n"
          + "      \"attribute1\":{\n"
          + "        \"StringValue\":\"attribute 1 value\",\n"
          + "        \"DataType\":\"String\"\n"
          + "      }\n"
          + "    },\n"
          + "    \"subject\":\"MySubject\",\n"
          + "    \"region\":\"us-east-1\",\n"
          + "    \"topicArn\":\"arn:aws:sns:us-east-1:000000000000:test\"\n"
          + "  }\n"
          + "}";

  protected static final Gson GSON = SnsGsonComponentSupplier.gsonInstance();
}
