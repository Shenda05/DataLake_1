SET NAMES utf8mb4;

SET @dataset_name = CONCAT('治理测试_订单脏数据_', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'));

INSERT INTO data_set(
    dataset_name,
    business_domain,
    source_id,
    format_type,
    record_count,
    field_count,
    storage_path,
    physical_table_name,
    description,
    creator,
    status,
    create_time,
    update_time
) VALUES (
    @dataset_name,
    'TRADE',
    NULL,
    'SQL',
    5,
    6,
    'manual://governance-demo',
    'pending_table_name',
    '用于测试数据治理的订单脏数据：重复订单、金额格式不一致、时间格式不一致、状态值不一致',
    1,
    'READY',
    NOW(),
    NOW()
);

SET @dataset_id = LAST_INSERT_ID();
SET @physical_table = CONCAT('dl_dataset_', @dataset_id);

UPDATE data_set
SET physical_table_name = @physical_table,
    update_time = NOW()
WHERE dataset_id = @dataset_id;

INSERT INTO meta_field(dataset_id, field_name, physical_column_name, field_type, nullable, sample_value, field_order, create_time)
VALUES
    (@dataset_id, 'order_id', 'order_id', 'VARCHAR', FALSE, 'O1001', 1, NOW()),
    (@dataset_id, 'amount', 'amount', 'VARCHAR', TRUE, '￥1,299.90', 2, NOW()),
    (@dataset_id, 'order_time', 'order_time', 'VARCHAR', TRUE, '2026/06/20 09:15:00', 3, NOW()),
    (@dataset_id, 'order_status', 'order_status', 'VARCHAR', TRUE, 'paid', 4, NOW()),
    (@dataset_id, 'product_name', 'product_name', 'VARCHAR', TRUE, '无线耳机', 5, NOW()),
    (@dataset_id, 'category', 'category', 'VARCHAR', TRUE, '手机配件', 6, NOW());

SET @create_sql = CONCAT(
    'CREATE TABLE ', @physical_table, ' (',
    'row_id BIGINT AUTO_INCREMENT PRIMARY KEY,',
    'order_id VARCHAR(1024),',
    'amount VARCHAR(1024),',
    'order_time VARCHAR(1024),',
    'order_status VARCHAR(1024),',
    'product_name VARCHAR(1024),',
    'category VARCHAR(1024)',
    ')'
);
PREPARE create_stmt FROM @create_sql;
EXECUTE create_stmt;
DEALLOCATE PREPARE create_stmt;

SET @insert_sql = CONCAT(
    'INSERT INTO ', @physical_table,
    ' (order_id, amount, order_time, order_status, product_name, category) VALUES ',
    '(''O1001'', ''￥1,299.90'', ''2026/06/20 09:15:00'', ''paid'', ''无线耳机'', ''手机配件''),',
    '(''O1001'', ''1299.9'', ''2026-06-20 09:15'', ''支付成功'', ''无线耳机'', ''手机配件''),',
    '(''O1002'', '' 88元 '', ''2026-06-21 10:30:00'', ''pending'', ''运动T恤'', ''服装''),',
    '(''O1003'', ''$45.678'', ''2026/06/22 11:00'', ''cancelled'', ''进口饼干'', ''食品''),',
    '(''O1004'', '''', ''2026-06-23'', ''failed'', ''电饭煲'', ''家电'')'
);
PREPARE insert_stmt FROM @insert_sql;
EXECUTE insert_stmt;
DEALLOCATE PREPARE insert_stmt;

SET @flow_name = CONCAT('治理测试_订单自动治理流程_', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'));

INSERT INTO governance_flow(flow_name, input_dataset_id, operator_chain, creator, create_time, update_time)
VALUES (
    @flow_name,
    @dataset_id,
    CAST(JSON_ARRAY(
        JSON_OBJECT('operatorKey', 'ORDER_DEDUP', 'params', JSON_OBJECT('field', 'order_id')),
        JSON_OBJECT('operatorKey', 'AMOUNT_NORMALIZE', 'params', JSON_OBJECT('field', 'amount')),
        JSON_OBJECT('operatorKey', 'TIME_NORMALIZE', 'params', JSON_OBJECT('field', 'order_time')),
        JSON_OBJECT('operatorKey', 'STATUS_NORMALIZE', 'params', JSON_OBJECT('field', 'order_status'))
    ) AS CHAR),
    1,
    NOW(),
    NOW()
);

SELECT
    @dataset_id AS dataset_id,
    @dataset_name AS dataset_name,
    @physical_table AS physical_table_name,
    LAST_INSERT_ID() AS flow_id,
    @flow_name AS flow_name;
