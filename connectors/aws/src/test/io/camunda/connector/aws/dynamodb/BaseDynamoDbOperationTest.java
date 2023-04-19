/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseDynamoDbOperationTest {
    @Mock
    protected DynamoDB dynamoDB;
    @Mock
    protected Table table;

    @BeforeEach
    public void beforeEach() {
        when(dynamoDB.getTable(TestData.Table.NAME)).thenReturn(table);
        when(table.describe()).thenReturn(new TableDescription().withTableName(TestData.Table.NAME));
    }

    public interface TestData {
        interface Table {
            String NAME = "my_table";
            String PARTITION_KEY = "ID";
            String PARTITION_KEY_ROLE_HASH = "HASH";
            String PARTITION_KEY_TYPE_NUMBER = "N";
            String SORT_KEY = "sortKey";
            String SORT_KEY_ROLE_RANGE = "RANGE";
            String SORT_KEY_TYPE_STRING = "S";
            Long READ_CAPACITY = 4L;
            Long WRITE_CAPACITY = 5L;
            String FILTER_EXPRESSION = "age >= :ageVal";
            Map<String, String> EXPRESSION_ATTRIBUTE_NAMES = Map.of("#name", "name");
            Map<String, Object> EXPRESSION_ATTRIBUTE_VALUES = Map.of(":ageVal", 30);
        }
    }
}
