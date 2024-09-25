## Version `v1.2.4` (25.09.2024)
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
