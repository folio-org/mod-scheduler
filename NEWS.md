## Version `v4.0.0` (in progress)
### Changes:
* Normalize cron notation to the Quartz format (MODSCHED-33)
* Introduce configuration for FSSP (APPPOCTOOL-59)
* Add migration for cron-based timers (MODSCHED-37)
* Use SECURE_STORE_ENV, not ENV, for secure store key (MODSCHED-45)
---

## Version `v3.0.0` (12.03.2025)
### Changes:
* Disable timers after app disabling (KEYCLOAK-20)
* Upgrade Java version to 21 (MODSCHED-32)
* Disable/Enable user timers upon module entitlement events (MODSCHED-25)
* Upgrade Spring Boot to 3.4.2 (MODSCHED-26)
* Upgrade keycloak-admin-client to v26.0.4 (KEYCLOAK-25)
* Integrate Internal Route Discovery into Sidecar with Dynamic Routing Switch, Replacing Kong (MODSCHED-24)
---

## Version `v2.0.0` (01.11.2024)
### Changes:
* Increase keycloak-admin-client to v25.0.6 (KEYCLOAK-24)
---

## Version `v1.3.0` (25.09.2024)
### Changes:
* Bumped up applications-poc-tools dependencies to 1.5.6 to support Hostname Verification for TLS connections
---

## Version `v1.2.3` (14.08.2024)
### Changes:
* Fix duplicated timers (MODSCHED-16)
---

## Version `v1.2.2` (10.07.2024)
### Changes:
* Upgrade keycloak-client to v25.0.1 (KEYCLOAK-11)
---

## Version `v1.2.1` (20.06.2024)
### Changes:
* Implemented timer events update while application upgrade (MODSCHED-8)
* Pack application to Docker Image and push into ECR (RANCHER-1515)
* Apply build SslContext from app-poc-tools lib with support of keystore custom type (APPPOCTOOL-20)
---

## Version `v1.2.0` (25.05.2024)
### Changes:
* Implement new event format from mgr-tenant-entitlements (MGRENTITLE-21)

---
## Version `v1.1.0` (16.04.2024)
### Changes:
* Added TLS support for keycloak clients (MODSCHED-9)
* Fixed Integer.MAX_VALUE as max value for default retries (EUREKA-66)
