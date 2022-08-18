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
import io.camunda.connector.suppliers.GsonComponentSupplier;

public abstract class BaseTest {

  protected static final String ACTUAL_QUEUE_URL = "https://sqs.region.amazonaws.com/camunda-test";
  protected static final String ACTUAL_QUEUE_REGION = "us-east-1";
  protected static final String SECRETS = "secrets.";
  protected static final String AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
  protected static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
  protected static final String SQS_QUEUE_URL = "SQS_QUEUE_URL";
  protected static final String SQS_QUEUE_REGION = "SQS_QUEUE_REGION";
  protected static final String ACTUAL_ACCESS_KEY = "4W553CR3TK3Y";
  protected static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  protected static final String SQS_MESSAGE_BODY = "{\"myKey\":\"myVal\"}";
  protected static final String WRONG_MESSAGE_BODY = "its wrong msg";
  protected static final String MSG_ID = "f3f7ac8f-2ff8-48a0-bb30-f220654f6a5f";

  // TODO: move to proper tests
  protected static final String DEFAULT_REQUEST_BODY =
      "{\n"
          + "    \"authentication\":{\n"
          + "      \"secretKey\":\"XXX\",\n"
          + "      \"accessKey\":\"YYY\"\n"
          + "    },\n"
          + "    \"queue\":{\n"
          + "      \"messageAttributes\":{\n"
          + "        \"description\":{\n"
          + "          \"StringValue\":\"delivery receipt from transaction 001122334455\",\n"
          + "          \"DataType\":\"String\"\n"
          + "        },\n"
          + "        \"size\":{\n"
          + "          \"StringValue\":\"2 KiB\",\n"
          + "          \"DataType\":\"String\"\n"
          + "        }\n"
          + "      },\n"
          + "      \"messageBody\":{\n"
          + "        \"data\":\"ok\"\n"
          + "      },\n"
          + "      \"region\":\"us-east-1\",\n"
          + "      \"url\":\"https://sqs.us-east-1.amazonaws.com/0000000/test-test-test\"\n"
          + "    }\n"
          + "  }";

  protected static final Gson GSON = GsonComponentSupplier.gsonInstance();
}
