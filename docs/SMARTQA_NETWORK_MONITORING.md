# Network monitoring

Config: `smartqa.intelligence.network-monitoring` default **true**, `network-retention` default **80**.

`NetworkObservation` stores method, URL, status, resource type, failed flag, failure string, timestamp. Classification: `NETWORK_FAILURE`, `AUTH_FAILURE` (401/403), `API_FAILURE` (5xx), `RESOURCE_FAILURE` (other 4xx), `OK`.

Bodies, cookies, and tokens are **not** stored. URLs must be treated as potentially sensitive in logs (`SecretMasker`).

This is diagnostic evidence for diagnosis/RAG queries. It does not drive clicks.
