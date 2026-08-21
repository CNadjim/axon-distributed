-- Tables Axon Framework
CREATE TABLE association_value_entry
(
    id                bigint       not null,
    association_key   varchar(255) not null,
    association_value varchar(255),
    saga_id           varchar(255) not null,
    saga_type         varchar(255),
    primary key (id)
);

CREATE TABLE dead_letter_entry
(
    dead_letter_id       varchar(255)                not null,
    cause_message        varchar(1023),
    cause_type           varchar(255),
    diagnostics          oid,
    enqueued_at          timestamp(6) with time zone not null,
    last_touched         timestamp(6) with time zone,
    aggregate_identifier varchar(255),
    event_identifier     varchar(255)                not null,
    message_type         varchar(255)                not null,
    meta_data            oid,
    payload              oid                         not null,
    payload_revision     varchar(255),
    payload_type         varchar(255)                not null,
    sequence_number      bigint,
    time_stamp           varchar(255)                not null,
    token                oid,
    token_type           varchar(255),
    type                 varchar(255),
    processing_group     varchar(255)                not null,
    processing_started   timestamp(6) with time zone,
    sequence_identifier  varchar(255)                not null,
    sequence_index       bigint                      not null,
    primary key (dead_letter_id)
);

CREATE TABLE domain_event_entry
(
    global_index         bigint       not null,
    event_identifier     varchar(255) not null,
    meta_data            oid,
    payload              oid          not null,
    payload_revision     varchar(255),
    payload_type         varchar(255) not null,
    time_stamp           varchar(255) not null,
    aggregate_identifier varchar(255) not null,
    sequence_number      bigint       not null,
    type                 varchar(255),
    primary key (global_index)
);

CREATE TABLE saga_entry
(
    saga_id         varchar(255) not null,
    revision        varchar(255),
    saga_type       varchar(255),
    serialized_saga oid,
    primary key (saga_id)
);

CREATE TABLE snapshot_event_entry
(
    aggregate_identifier varchar(255) not null,
    sequence_number      bigint       not null,
    type                 varchar(255) not null,
    event_identifier     varchar(255) not null,
    meta_data            oid,
    payload              oid          not null,
    payload_revision     varchar(255),
    payload_type         varchar(255) not null,
    time_stamp           varchar(255) not null,
    primary key (aggregate_identifier, sequence_number, type)
);

CREATE TABLE token_entry
(
    processor_name varchar(255) not null,
    segment        integer      not null,
    owner          varchar(255),
    timestamp      varchar(255) not null,
    token          oid,
    token_type     varchar(255),
    primary key (processor_name, segment)
);

-- Table db-scheduler
CREATE TABLE scheduled_tasks
(
    task_name            text                     not null,
    task_instance        text                     not null,
    task_data            bytea,
    execution_time       timestamp with time zone not null,
    picked               boolean                  not null,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures int,
    last_heartbeat       timestamp with time zone,
    version              bigint                   not null,
    PRIMARY KEY (task_name, task_instance)
);

-- Index pour association_value_entry
CREATE INDEX idx_assoc_value_entry_saga_type_key_value ON association_value_entry (saga_type, association_key, association_value);
CREATE INDEX idx_assoc_value_entry_saga_id_type ON association_value_entry (saga_id, saga_type);

-- Index pour dead_letter_entry
CREATE INDEX idx_dead_letter_entry_processing_group ON dead_letter_entry (processing_group);
CREATE INDEX idx_dead_letter_entry_group_sequence_id ON dead_letter_entry (processing_group, sequence_identifier);

-- Index pour scheduled_tasks
CREATE INDEX idx_scheduled_tasks_execution_time ON scheduled_tasks (execution_time);
CREATE INDEX idx_scheduled_tasks_last_heartbeat ON scheduled_tasks (last_heartbeat);

-- Contraintes d'unicité
ALTER TABLE dead_letter_entry
    ADD CONSTRAINT uk_dead_letter_entry_group_seq_id_idx UNIQUE (processing_group, sequence_identifier, sequence_index);
ALTER TABLE domain_event_entry
    ADD CONSTRAINT uk_domain_event_entry_aggregate_id_seq_num UNIQUE (aggregate_identifier, sequence_number);
ALTER TABLE domain_event_entry
    ADD CONSTRAINT uk_domain_event_entry_event_id UNIQUE (event_identifier);
ALTER TABLE snapshot_event_entry
    ADD CONSTRAINT uk_snapshot_event_entry_event_id UNIQUE (event_identifier);

-- Séquences
CREATE SEQUENCE association_value_entry_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE domain_event_entry_seq START WITH 1 INCREMENT BY 50;
