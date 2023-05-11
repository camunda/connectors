/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

interface TestData {
    interface Authentication {
        interface Secrets {
            String ACCESS_KEY = "ACCESS_KEY";
            String SECRET_KEY = "SECRET_KEY";
        }
        interface ActualValue {
            String ACCESS_KEY = "access_key0932490";
            String SECRET_KEY = "secret_key12314";
        }
    }
    interface Configuration {
        interface Secrets {
            String REGION = "REGION_KEY";
        }
        interface ActualValue {
            String REGION = "es-east-1";
        }
    }
}
