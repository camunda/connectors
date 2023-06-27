/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.common.suppliers.ObjectMapperSupplier;

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

  protected static final String DEFAULT_REQUEST_BODY_WITH_JSON_PAYLOAD =
      """
                  {
                      "authentication":{
                        "secretKey":"XXX",
                        "accessKey":"YYY"
                      },
                      "queue":{
                        "messageAttributes":{
                          "description":{
                            "StringValue":"delivery receipt from transaction 001122334455",
                            "DataType":"String"
                          },
                          "size":{
                            "StringValue":"2 KiB",
                            "DataType":"String"
                          }
                        },
                        "messageBody":{
                          "data":"ok"
                        },
                        "region":"us-east-1",
                        "url":"https://sqs.us-east-1.amazonaws.com/0000000/test-test-test"
                      }
                    }""";

  protected static final String DEFAULT_REQUEST_BODY_WITH_STRING_PAYLOAD =
      """
                  {
                      "authentication":{
                        "secretKey":"XXX",
                        "accessKey":"YYY"
                      },
                      "queue":{
                        "messageAttributes":{
                          "description":{
                            "StringValue":"delivery receipt from transaction 001122334455",
                            "DataType":"String"
                          },
                          "size":{
                            "StringValue":"2 KiB",
                            "DataType":"String"
                          }
                        },
                        "messageBody": "I am a string value!",
                        "region":"us-east-1",
                        "url":"https://sqs.us-east-1.amazonaws.com/0000000/test-test-test"
                      }
                    }""";

  protected static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
}
