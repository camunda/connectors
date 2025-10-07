/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.util;

import io.camunda.connector.textract.model.DocumentLocationType;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;

public class TextractTestUtils {

  public static final String ACTUAL_ACCESS_KEY = "DDDCCCBBBBAAAA";
  public static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  public static final String ASYNC_EXECUTION_JSON =
      """
                {
                   "input": {
                      "accept": "application/json",
                      "executionType": "ASYNC",
                      "documentS3Bucket": "bucket",
                      "documentName": "file.png",
                      "documentVersion": "2",
                      "analyzeTables": true,
                      "analyzeForms": true,
                      "analyzeSignatures": true,
                      "analyzeLayout": true,
                      "clientRequestToken": "token",
                      "jobTag": "jobId",
                      "kmsKeyId": "keyId",
                      "notificationChannelRoleArn": "roleArn",
                      "notificationChannelSnsTopicArn": "topicArn",
                      "outputConfigS3Bucket": "bucket",
                      "outputConfigS3Prefix": "outputPrefix"
                   },
                   "configuration": {
                      "region": "eu-central-1"
                   },
                   "authentication": {
                      "type": "defaultCredentialsChain",
                      "accessKey": "{{secrets.ACCESS_KEY}}",
                      "secretKey": "{{secrets.SECRET_KEY}}"
                   }
                }
            """;

  public static final String ASYNC_EXECUTION_JSON_WITHOUT_S3_BUCKET_OUTPUT =
      """
                    {
                       "input": {
                          "accept": "application/json",
                          "executionType": "ASYNC",
                          "documentS3Bucket": "bucket",
                          "documentName": "file.png",
                          "documentVersion": "2",
                          "analyzeTables": true,
                          "analyzeForms": true,
                          "analyzeSignatures": true,
                          "analyzeLayout": true,
                          "clientRequestToken": "token",
                          "jobTag": "jobId",
                          "kmsKeyId": "keyId",
                          "notificationChannelRoleArn": "roleArn",
                          "notificationChannelSnsTopicArn": "topicArn",
                          "outputConfigS3Bucket": "",
                          "outputConfigS3Prefix": "outputPrefix"
                       },
                       "configuration": {
                          "region": "eu-central-1"
                       },
                       "authentication": {
                          "type": "defaultCredentialsChain",
                          "accessKey": "{{secrets.ACCESS_KEY}}",
                          "secretKey": "{{secrets.SECRET_KEY}}"
                       }
                    }
                """;
  public static final String ASYNC_EXECUTION_JSON_WITH_ROLE_ARN_AND_WITHOUT_SNS_TOPIC =
      """
                        {
                           "input": {
                              "accept": "application/json",
                              "executionType": "ASYNC",
                              "documentS3Bucket": "bucket",
                              "documentName": "file.png",
                              "documentVersion": "2",
                              "analyzeTables": true,
                              "analyzeForms": true,
                              "analyzeSignatures": true,
                              "analyzeLayout": true,
                              "clientRequestToken": "token",
                              "jobTag": "jobId",
                              "kmsKeyId": "keyId",
                              "notificationChannelRoleArn": "roleArn",
                              "notificationChannelSnsTopicArn": "",
                              "outputConfigS3Bucket": "bucket",
                              "outputConfigS3Prefix": "outputPrefix"
                           },
                           "configuration": {
                              "region": "eu-central-1"
                           },
                           "authentication": {
                              "type": "defaultCredentialsChain",
                              "accessKey": "{{secrets.ACCESS_KEY}}",
                              "secretKey": "{{secrets.SECRET_KEY}}"
                           }
                        }
                    """;

  public static final String ASYNC_EXECUTION_JSON_WITH_SNS_TOPIC_AND_WITHOUT_ROLE_ARN =
      """
                        {
                           "input": {
                              "accept": "application/json",
                              "executionType": "ASYNC",
                              "documentS3Bucket": "bucket",
                              "documentName": "file.png",
                              "documentVersion": "2",
                              "analyzeTables": true,
                              "analyzeForms": true,
                              "analyzeSignatures": true,
                              "analyzeLayout": true,
                              "clientRequestToken": "token",
                              "jobTag": "jobId",
                              "kmsKeyId": "keyId",
                              "notificationChannelRoleArn": "",
                              "notificationChannelSnsTopicArn": "snsTopic",
                              "outputConfigS3Bucket": "bucket",
                              "outputConfigS3Prefix": "outputPrefix"
                           },
                           "configuration": {
                              "region": "eu-central-1"
                           },
                           "authentication": {
                              "type": "defaultCredentialsChain",
                              "accessKey": "{{secrets.ACCESS_KEY}}",
                              "secretKey": "{{secrets.SECRET_KEY}}"
                           }
                        }
                    """;
  public static final String SYNC_EXECUTION_JSON =
      """
                {
                   "input": {
                      "accept": "application/json",
                      "executionType": "SYNC",
                      "documentS3Bucket": "bucket",
                      "documentName": "file.png",
                      "documentVersion": "2",
                      "analyzeTables": true,
                      "analyzeForms": true,
                      "analyzeSignatures": true,
                      "analyzeLayout": true

                   },
                   "configuration": {
                      "region": "eu-central-1"
                   },
                   "authentication": {
                      "type": "defaultCredentialsChain",
                      "accessKey": "{{secrets.ACCESS_KEY}}",
                      "secretKey": "{{secrets.SECRET_KEY}}"
                   }
                }
            """;

  public static final String POLLING_EXECUTION_JSON =
      """
                {
                   "input": {
                      "accept": "application/json",
                      "executionType": "POLLING",
                      "documentS3Bucket": "bucket",
                      "documentName": "file.png",
                      "documentVersion": "2",
                      "analyzeTables": true,
                      "analyzeForms": true,
                      "analyzeSignatures": true,
                      "analyzeLayout": true

                   },
                   "configuration": {
                      "region": "eu-central-1"
                   },
                   "authentication": {
                      "type": "defaultCredentialsChain",
                      "accessKey": "{{secrets.ACCESS_KEY}}",
                      "secretKey": "{{secrets.SECRET_KEY}}"
                   }
                }
            """;

  public static final TextractRequestData FULL_FILLED_ASYNC_TEXTRACT_DATA =
      new TextractRequestData(
          DocumentLocationType.S3,
          "test-bucket",
          "test-object",
          "1",
          null,
          TextractExecutionType.ASYNC,
          true,
          true,
          true,
          true,
          false,
          "",
          "token",
          "jobTag",
          "kmsId",
          "notification-channel",
          "sns-arn",
          "outputBucket",
          "prefix");
}
