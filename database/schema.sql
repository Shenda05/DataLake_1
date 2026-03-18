CREATE TABLE role (
    role_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(64) NOT NULL UNIQUE,
    role_desc VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES role(role_id)
);

CREATE TABLE data_source (
    source_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_name VARCHAR(128) NOT NULL UNIQUE,
    source_type VARCHAR(32) NOT NULL,
    host VARCHAR(128),
    port INT,
    db_name VARCHAR(128),
    username VARCHAR(64),
    password VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    description VARCHAR(255),
    create_user BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE data_set (
    dataset_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_name VARCHAR(128) NOT NULL UNIQUE,
    source_id BIGINT,
    format_type VARCHAR(32) NOT NULL,
    record_count BIGINT DEFAULT 0,
    field_count INT DEFAULT 0,
    storage_path VARCHAR(255),
    description VARCHAR(255),
    creator BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dataset_source FOREIGN KEY (source_id) REFERENCES data_source(source_id)
);

CREATE TABLE meta_field (
    field_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_type VARCHAR(64) NOT NULL,
    nullable TINYINT(1) DEFAULT 1,
    default_value VARCHAR(255),
    field_desc VARCHAR(255),
    sample_value VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_meta_dataset FOREIGN KEY (dataset_id) REFERENCES data_set(dataset_id)
);

CREATE TABLE operator_def (
    operator_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_name VARCHAR(128) NOT NULL,
    operator_key VARCHAR(64) NOT NULL UNIQUE,
    operator_type VARCHAR(32) NOT NULL,
    config_schema JSON,
    description VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE governance_flow (
    flow_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flow_name VARCHAR(128) NOT NULL UNIQUE,
    input_dataset_id BIGINT NOT NULL,
    operator_chain JSON NOT NULL,
    output_dataset_id BIGINT,
    creator BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_flow_input_dataset FOREIGN KEY (input_dataset_id) REFERENCES data_set(dataset_id),
    CONSTRAINT fk_flow_output_dataset FOREIGN KEY (output_dataset_id) REFERENCES data_set(dataset_id)
);

CREATE TABLE task (
    task_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_name VARCHAR(128) NOT NULL UNIQUE,
    task_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    cron_expr VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    create_user BIGINT,
    description VARCHAR(255),
    next_run_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE task_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    duration BIGINT DEFAULT 0,
    operator_user BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_log_task FOREIGN KEY (task_id) REFERENCES task(task_id)
);
