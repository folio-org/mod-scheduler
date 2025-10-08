# mod-scheduler

Copyright (C) 2023-2024 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Table of contents

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Running It](#running-it)
* [Docker](#docker)
* [Environment variables](#environment-variables)
  * [Kafka environment variables](#kafka-environment-variables)
  * [Retry environment variables](#retry-environment-variables)
  * [Secure storage environment variables](#secure-storage-environment-variables)
    * [AWS-SSM](#aws-ssm)
    * [VAULT](#vault)
    * [Folio Secure Store Proxy (FSSP)](#folio-secure-store-proxy-fssp)
  * [Quartz configuration properties](#quartz-configuration-properties)
  * [Cron format for timers](#cron-format-for-timers)

## Introduction

`mod-scheduler` provides following functionality:

* schedule recurring jobs and/or events to happen at predefined times/intervals.

## Compiling

```shell
mvn clean install
```

To compile project without tests

```shell
mvn clean install -DskipTests=true
```

See that it says "BUILD SUCCESS" near the end.

## Running It

Run locally with proper environment variables set (see [Environment variables](#environment-variables) below) on
listening port 8081 (default listening port):

```shell
java \
  -Dserver.port=8081 \
  -DDB_HOST=localhost \
  -DDB_PORT=5432 \
  -DDB_DATABASE=postgres \
  -DDB_USERNAME=postgres \
  -DDB_PASSWORD=mysecretpassword \
  -Dokapi.url=http://localhost:9130 \
  -Dokapi.token=${okapiToken} \
  -jar target/mod-scheduler-*.jar
```

## Docker

This method will require [PostgreSQL](https://hub.docker.com/_/postgres/) database running as docker container.

```shell
docker run \
  --name postgres \
  -e PGUSER=postgres \
  -e POSTGRES_USERNAME=postgres \
  -e POSTGRES_PASSWORD=mysecretpassword \
  -p 5432:5432 \
  -d postgres:16-alpine
```

Build the docker container with:

```shell
docker build -t mod-scheduler .
```

Test that it runs with:

```shell
docker run \
  --name mod-scheduler \
  --link postgres:postgres \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_DATABASE=postgres \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=mysecretpassword \
  -e okapi.url=http://okapi:9130 \
  -e okapi.token=${okapiToken} \
  -p 8081:8081 \
  -d mod-scheduler
```

## Environment variables

| Name                              | Default value          | Description                                                                                                                                                           |
|:----------------------------------|:-----------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DB_HOST                           | localhost              | Postgres hostname                                                                                                                                                     |
| DB_PORT                           | 5432                   | Postgres port                                                                                                                                                         |
| DB_USERNAME                       | postgres               | Postgres username                                                                                                                                                     |
| DB_PASSWORD                       | postgres               | Postgres username password                                                                                                                                            |
| DB_DATABASE                       | okapi_modules          | Postgres database name                                                                                                                                                |
| QUARTZ_STARTUP_DELAY              | 0s                     | Quartz initialization delay                                                                                                                                           |
| QUARTZ_SCHEDULER_IDLE_WAIT_TIME   | 10000                  | Is the amount of time in milliseconds that the scheduler will wait before re-queries for available triggers                                                           |
| QUARTZ_MISFIRE_THRESHOLD          | 250                    | The number of milliseconds the scheduler will 'tolerate' a trigger to pass its next-fire-time                                                                         |
| QUARTZ_CLUSTER_CHECKIN_INTERVAL   | 500                    | Set the frequency (in milliseconds) at which this instance "checks-in"* with the other instances of the cluster. Affects the quickness of detecting failed instances. |
| QUARTZ_POOL_THREAD_COUNT          | 5                      | The number of threads that are available for concurrent execution of jobs.                                                                                            |
| okapi.url                         | -                      | Okapi URL used to perform HTTP requests for recurring jobs, required.                                                                                                 |
| OKAPI_URL                         | -                      | Alias for `okapi.url`.                                                                                                                                                |
| SECRET_STORE_TYPE                 | VAULT                  | Secure storage type. Supported values: `EPHEMERAL`, `AWS_SSM`, `VAULT`, `FSSP`, required.                                                                             |
| KC_INTEGRATION_ENABLED            | true                   | Defines if Keycloak integration is enabled or disabled.<br/>If it set to `false` - it will exclude all keycloak-related beans from spring context.                    |
| KC_URL                            | http://keycloak:8080   | Keycloak URL used to perform HTTP requests.                                                                                                                           |
| KC_IMPERSONATION_CLIENT           | impersonation-client   | Defined client in Keycloak, that has permissions to impersonate users.                                                                                                |
| KC_ADMIN_CLIENT_ID                | be-admin-client        | Keycloak admin client id.                                                                                                                                             |
| SYSTEM_USER_USERNAME_TEMPLATE     | {tenantId}-system-user | System user username template, used to generate system user `username`                                                                                                |
| KC_CLIENT_TLS_ENABLED             | false                  | Enables TLS for keycloak clients.                                                                                                                                     |
| KC_CLIENT_TLS_TRUSTSTORE_PATH     | -                      | Truststore file path for keycloak clients.                                                                                                                            |
| KC_CLIENT_TLS_TRUSTSTORE_PASSWORD | -                      | Truststore password for keycloak clients.                                                                                                                             |
| KC_CLIENT_TLS_TRUSTSTORE_TYPE     | -                      | Truststore file type for keycloak clients.                                                                                                                            |
| CLIENT_SECRET_KEY_CACHE_MAX_SIZE  | 200                    | Property sets the maximum number of client secret keys that can be stored in the cache                                                                                |
| CLIENT_SECRET_KEY_CACHE_TTL       | 6000s                  | Property specifies the time-to-live for each cache entry                                                                                                              |

### Kafka environment variables

| Name                            | Default value                                                        | Description                                                                                                                                                |
|:--------------------------------|:---------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| KAFKA_HOST                      | kafka                                                                | Kafka broker hostname                                                                                                                                      |
| KAFKA_PORT                      | 9092                                                                 | Kafka broker port                                                                                                                                          |
| KAFKA_SECURITY_PROTOCOL         | PLAINTEXT                                                            | Kafka security protocol used to communicate with brokers (SSL or PLAINTEXT)                                                                                |
| KAFKA_SSL_KEYSTORE_LOCATION     | -                                                                    | The location of the Kafka key store file. This is optional for client and can be used for two-way authentication for client.                               |
| KAFKA_SSL_KEYSTORE_PASSWORD     | -                                                                    | The store password for the Kafka key store file. This is optional for client and only needed if 'ssl.keystore.location' is configured.                     |
| KAFKA_SSL_TRUSTSTORE_LOCATION   | -                                                                    | The location of the Kafka trust store file.                                                                                                                |
| KAFKA_SSL_TRUSTSTORE_PASSWORD   | -                                                                    | The password for the Kafka trust store file. If a password is not set, trust store file configured will still be used, but integrity checking is disabled. |
| KAFKA_JOB_CONSUMER_PATTERN      | (${folio.environment}\.)(.*\.)mgr-tenant-entitlements\.scheduled-job | Custom subscription pattern for Kafka consumers.                                                                                                           |
| KAFKA_JOB_CONCURRENCY           | 1                                                                    | Custom number of kafka concurrent threads for message consuming.                                                                                           |
| KAFKA_CONSUMER_MAX_POLL_RECORDS | 200                                                                  | Maximum number of records returned in a single call to poll().                                                                                             |

### Retry environment variables

| Name                              | Default value | Description                                                                                                                             |
|:----------------------------------|:--------------|:----------------------------------------------------------------------------------------------------------------------------------------|
| SYSTEM_USER_RETRY_DELAY           | 1s            | Retry delay between attempts to retrieve system user                                                                                    |
| SYSTEM_USER_MAX_DELAY             | 1m            | Maximum delay between attempts to retrieve system user                                                                                  |
| SYSTEM_USER_RETRY_ATTEMPTS        | 2147483647    | Number of retry attempts to retrieve system user (default value is Long.MAX_VALUE ~= infinite amount of retries)                        |
| SYSTEM_USER_RETRY_MULTIPLIER      | 1.5           | Retry attempts delay multiplier to retrieve system user                                                                                 |
| SCHEDULED_TIMER_EVENT_RETRY_DELAY | 1s            | Retry delay between attempts to process event from `scheduled-job` Kafka topic                                                          |
| SCHEDULED_TIMER_EVENT_ATTEMPTS    | 2147483647    | Number of attempts to process event from `scheduled-job` Kafka topic (default value is Integer.MAX_VALUE ~= infinite amount of retries) |

### Secure storage environment variables

| Name                | Default value | Description                                                                                                                                                    |
|:--------------------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SECURE_STORE_ENV    | folio         | First segment of the secure store key, for example `prod` or `test`. Defaults to `folio`. In Ramsons and Sunflower defaults to ENV with fall-back `folio`.     |

#### AWS-SSM

Required when `SECRET_STORE_TYPE=AWS_SSM`

| Name                                          | Default value | Description                                                                                                                                                    |
|:----------------------------------------------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SECRET_STORE_AWS_SSM_REGION                   | -             | The AWS region to pass to the AWS SSM Client Builder. If not set, the AWS Default Region Provider Chain is used to determine which region to use.              |
| SECRET_STORE_AWS_SSM_USE_IAM                  | true          | If true, will rely on the current IAM role for authorization instead of explicitly providing AWS credentials (access_key/secret_key)                           |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT | -             | The HTTP endpoint to use for retrieving AWS credentials. This is ignored if useIAM is true                                                                     |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH     | -             | The path component of the credentials endpoint URI. This value is appended to the credentials endpoint to form the URI from which credentials can be obtained. |

#### VAULT

Required when `SECRET_STORE_TYPE=VAULT`

| Name                                    | Default value | Description                                                                         |
|:----------------------------------------|:--------------|:------------------------------------------------------------------------------------|
| SECRET_STORE_VAULT_TOKEN                | -             | token for accessing vault, may be a root token                                      |
| SECRET_STORE_VAULT_ADDRESS              | -             | the address of your vault                                                           |
| SECRET_STORE_VAULT_ENABLE_SSL           | false         | whether or not to use SSL                                                           |
| SECRET_STORE_VAULT_PEM_FILE_PATH        | -             | the path to an X.509 certificate in unencrypted PEM format, using UTF-8 encoding    |
| SECRET_STORE_VAULT_KEYSTORE_PASSWORD    | -             | the password used to access the JKS keystore (optional)                             |
| SECRET_STORE_VAULT_KEYSTORE_FILE_PATH   | -             | the path to a JKS keystore file containing a client cert and private key            |
| SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH | -             | the path to a JKS truststore file containing Vault server certs that can be trusted |

#### Folio Secure Store Proxy (FSSP)

Required when `SECRET_STORE_TYPE=FSSP`

| Name                                   | Default value         | Description                                          |
|:---------------------------------------|:----------------------|:-----------------------------------------------------|
| SECRET_STORE_FSSP_ADDRESS              | -                     | The address (URL) of the FSSP service.               |
| SECRET_STORE_FSSP_SECRET_PATH          | secure-store/entries  | The path in FSSP where secrets are stored/retrieved. |
| SECRET_STORE_FSSP_ENABLE_SSL           | false                 | Whether to use SSL when connecting to FSSP.          |
| SECRET_STORE_FSSP_TRUSTSTORE_PATH      | -                     | Path to the truststore file for SSL connections.     |
| SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE | -                     | The type of the truststore file (e.g., JKS, PKCS12). |
| SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD  | -                     | The password for the truststore file.                |

### Quartz configuration properties

`mod-scheduler` uses `spring-boot-starter-quartz` in cluster mode. Required configuration properties defined
in [application.yml](./src/main/resources/application.yml) under `spring.quartz` section.

In addition, Quartz can be tuned
using [Quart configuration properties](http://www.quartz-scheduler.org/documentation/2.4.0-SNAPSHOT/configuration.html)


### Cron format for timers
`mod-scheduler` supports both Unix and Quartz cron formats for timers. The Unix format is automatically converted to Quartz, so you can use either format for cron-based timers. The formats are as follows:

**Unix cron format:**

```
<minute> <hour> <day-of-month> <month> <day-of-week>
```
**Quartz cron format:**
```
<second> <minute> <hour> <day-of-month> <month> <day-of-week> [year]
```
