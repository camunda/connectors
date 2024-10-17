/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

public class ComprehendTestUtils {

  public static final String ACTUAL_ACCESS_KEY = "DDDCCCBBBBAAAA";
  public static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";

  public static final String SYNC_EXECUTION_JSON =
      """
                    {
                             "input":{
                                "accept":"application/json",
                                "type":"sync",
                                "documentReadAction":"TEXTRACT_DETECT_DOCUMENT_TEXT",
                                "documentReadMode":"SERVICE_DEFAULT",
                                "featureTypeTables":true,
                                "featureTypeForms":true,
                                "text":"plain text",
                                "endpointArn":"endpoint"
                             },
                             "configuration":{
                                "region":"eu-central-1"
                             },
                             "authentication":{
                                "type":"defaultCredentialsChain",
                                "accessKey":"{{secrets.ACCESS_KEY}}",
                                "secretKey":"{{secrets.SECRET_KEY}}"
                             }
                          }

                        """;

  public static final String ASYNC_EXECUTION_JSON =
      """
                    {
                       "input":{
                          "accept":"application/json",
                          "type":"async",
                          "documentReadAction":"TEXTRACT_DETECT_DOCUMENT_TEXT",
                          "documentReadMode":"SERVICE_DEFAULT",
                          "featureTypeTables":true,
                          "featureTypeForms":true,
                          "inputS3Uri":"input",
                          "comprehendInputFormat":"ONE_DOC_PER_FILE",
                          "clientRequestToken":"token",
                          "dataAccessRoleArn":"arn",
                          "documentClassifierArn":"arn",
                          "flywheelArn":"arn",
                          "jobName":"job",
                          "outputS3Uri":"output",
                          "outputKmsKeyId":"kms",
                          "tags": {"status": "active"},
                          "volumeKmsKeyId": "volumeKms",
                          "securityGroupIds": ["sc-1"],
                          "subnets":["sb-1"]
                       },
                       "configuration":{
                          "region":"eu-central-1"
                       },
                       "authentication":{
                          "type":"defaultCredentialsChain",
                          "accessKey":"{{secrets.ACCESS_KEY}}",
                          "secretKey":"{{secrets.SECRET_KEY}}"
                       }
                    }
                                            """;
}
