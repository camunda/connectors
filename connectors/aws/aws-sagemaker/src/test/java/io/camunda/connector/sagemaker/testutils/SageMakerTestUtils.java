/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.testutils;

public class SageMakerTestUtils {

  public static final String ACTUAL_ACCESS_KEY = "DDDCCCBBBBAAAA";
  public static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";

  public static final String REAL_TIME_EXECUTION_JSON =
      """
          {
             "input": {
                "body": {
                   "inputs": {
                      "value": "The answer to life, the universe, and everything is",
                      "params": {
                         "temperature": 0.3
                      }
                   }
                },
                "accept": "application/json",
                "inferenceComponentName": "component01",
                "invocationType": "sync",
                "targetVariant": "variant01",
                "inferenceId": "inference01",
                "contentType": "application/json",
                "enableExplanations": "not_set",
                "targetModel": "model01",
                "endpointName": "jumpstart-dft-mx-od-ssd-512-123456-123456",
                "customAttributes": "custom-attr",
                "targetContainerHostname": "host01"
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

  public static final String ASYNC_EXECUTION_JSON =
      """
          {
             "input": {
                "accept": "application/json",
                "inputLocation": "s3://my-bucket/objectFolder/object",
                "invocationType": "async",
                "invocationTimeoutSeconds": "300",
                "requestTTLSeconds": "600",
                "inferenceId": "inference01",
                "contentType": "application/json",
                "customAttributes": "custom-attr",
                "endpointName": "jumpstart-dft-mx-od-ssd-512-123456-123456"
             },
             "configuration": {
                "region": "eu-central-1"
             },
             "authentication": {
                "type": "credentials",
                "accessKey": "{{secret.ACCESS_KEY}}",
                "secretKey": "{{secrets.SECRET_KEY}}"
             }
          }
      """;
}
