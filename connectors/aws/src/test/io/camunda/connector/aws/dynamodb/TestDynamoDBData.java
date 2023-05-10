/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import java.util.Map;

public interface TestDynamoDBData {
    interface Secrets {
        String TABLE_NAME = "TABLE_NAME_KEY";
        String ITEM_KEY = "ITEM_KEY_SECRET";
        String ITEM_VALUE = "ITEM_VALUE";
        String KEY_ATTRIBUTE_VALUE = "KEY_ATTRIBUTE_VALUE";
        String PARTITION_KEY = "PARTITION_KEY";
        String SORT_KEY = "SORT_KEY";
        String FILTER_EXPRESSION = "FILTER_EXPRESSION_KEY";
        String PROJECTION_EXPRESSION = "PROJECTION_KEY";
        String EXPRESSION_ATTRIBUTE_NAME = "EXPRESSION_ATTRIBUTE_NAME";
        String EXPRESSION_ATTRIBUTE_VALUE = "EXPRESSION_ATTRIBUTE_VALUE";
    }

    interface ActualValue {

        String TABLE_NAME = "my_table";
        String ITEM_KEY = "item key";
        String ITEM_VALUE = "item value";
        String KEY_ATTRIBUTE_VALUE = "1234";
        String PARTITION_KEY = "ID";

        String SORT_KEY = "sortKey";
        String FILTER_EXPRESSION = "age >= :ageVal";
        String PROJECTION_EXPRESSION = "Category, Stat, description";
        String EXPRESSION_ATTRIBUTE_NAME = "name";
        String EXPRESSION_ATTRIBUTE_VALUE = "30L";
        String PARTITION_KEY_ROLE_HASH = "HASH";
        String PARTITION_KEY_TYPE_NUMBER = "N";
        String SORT_KEY_ROLE_RANGE = "RANGE";
        String SORT_KEY_TYPE_STRING = "S";
        Long READ_CAPACITY = 4L;
        Long WRITE_CAPACITY = 5L;
        Map<String, String> EXPRESSION_ATTRIBUTE_NAMES = Map.of("#name", "name");
        Map<String, Object> EXPRESSION_ATTRIBUTE_VALUES = Map.of(":ageVal", 30);

    }


}
