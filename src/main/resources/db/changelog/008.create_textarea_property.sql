-- liquibase formatted sql
-- changeset palmenhq:008

ALTER TYPE property_value_type
    ADD VALUE 'textarea';

-- rollback alter type property_value_type drop value 'textarea'
