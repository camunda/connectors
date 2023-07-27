/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;

public abstract class BaseTest {

  protected static final String AWS_TOPIC_REGION = "AWS_TOPIC_REGION";
  protected static final String ACTUAL_TOPIC_REGION = "us-east-1";
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
      """
                  {
                    "authentication":{
                      "secretKey":"abc",
                      "accessKey":"def"
                    },
                    "topic":{
                      "message":"MyMessage",
                      "messageAttributes":{
                        "attribute2":{
                          "StringValue":"attribute 2 value",
                          "DataType":"String"
                        },
                        "attribute1":{
                          "StringValue":"attribute 1 value",
                          "DataType":"String"
                        }
                      },
                      "subject":"MySubject",
                      "region":"us-east-1",
                      "topicArn":"arn:aws:sns:us-east-1:000000000000:test"
                    }
                  }""";

  protected static final String REQUEST_WITH_JSON_MSG_BODY =
      """
                  {
                    "authentication":{
                      "secretKey":"abc",
                      "accessKey":"def"
                    },
                    "topic":{
                      "message":{"key":"value"},
                      "messageAttributes":{
                        "attribute2":{
                          "StringValue":"attribute 2 value",
                          "DataType":"String"
                        },
                        "attribute1":{
                          "StringValue":"attribute 1 value",
                          "DataType":"String"
                        }
                      },
                      "subject":"MySubject",
                      "region":"us-east-1",
                      "topicArn":"arn:aws:sns:us-east-1:000000000000:test"
                    }
                  }""";

  protected static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
}
