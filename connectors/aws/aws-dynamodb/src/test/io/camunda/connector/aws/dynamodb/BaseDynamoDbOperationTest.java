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
import com.google.gson.Gson;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseDynamoDbOperationTest {
    protected final static Gson GSON = GsonDynamoDbComponentSupplier.gsonInstance();
    @Mock
    protected DynamoDB dynamoDB;
    @Mock
    protected Table table;

    @BeforeEach
    public void beforeEach() {
        when(dynamoDB.getTable(TestDynamoDBData.ActualValue.TABLE_NAME)).thenReturn(table);
        when(table.describe()).thenReturn(new TableDescription().withTableName(TestDynamoDBData.ActualValue.TABLE_NAME));
    }

    public OutboundConnectorContext getContextWithSecrets(){
        return OutboundConnectorContextBuilder.create()
                .secret(TestDynamoDBData.Secrets.TABLE_NAME, TestDynamoDBData.ActualValue.TABLE_NAME)
                .secret(TestDynamoDBData.Secrets.ITEM_KEY, TestDynamoDBData.ActualValue.ITEM_KEY)
                .secret(TestDynamoDBData.Secrets.ITEM_VALUE, TestDynamoDBData.ActualValue.ITEM_VALUE)
                .secret(TestDynamoDBData.Secrets.KEY_ATTRIBUTE_VALUE, TestDynamoDBData.ActualValue.KEY_ATTRIBUTE_VALUE)
                .secret(TestDynamoDBData.Secrets.PARTITION_KEY, TestDynamoDBData.ActualValue.PARTITION_KEY)
                .secret(TestDynamoDBData.Secrets.SORT_KEY, TestDynamoDBData.ActualValue.SORT_KEY)
                .secret(TestDynamoDBData.Secrets.FILTER_EXPRESSION, TestDynamoDBData.ActualValue.FILTER_EXPRESSION)
                .secret(TestDynamoDBData.Secrets.PROJECTION_EXPRESSION, TestDynamoDBData.ActualValue.PROJECTION_EXPRESSION)
                .secret(TestDynamoDBData.Secrets.EXPRESSION_ATTRIBUTE_NAME, TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_NAME)
                .secret(TestDynamoDBData.Secrets.EXPRESSION_ATTRIBUTE_VALUE, TestDynamoDBData.ActualValue.EXPRESSION_ATTRIBUTE_VALUE)
                .build();
    }

}
