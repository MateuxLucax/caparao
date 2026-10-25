# 17. Ask for the local network permission when the server is local

Status: Accepted

## Context

The app targets SDK 37. On Android 17, apps targeting 37 or higher have local network access blocked by default and
need the runtime permission `ACCESS_LOCAL_NETWORK` (protection level dangerous, permission group `NEARBY_DEVICES`).
Blocked TCP connections typically fail with a timeout. DNS to a local resolver is exempt. Apps targeting 36 or lower
get an implicit grant through `INTERNET`. See
[Local network permission](https://developer.android.com/privacy-and-security/local-network-permission) and
[Local network definition](https://developer.android.com/privacy-and-security/local-network-definition).

Many users run the server at home (the stub, or the exporter on a NAS). health-connect-bridge hit this as a "Test
connection" that timed out after 15 s with no request reaching the server, and solved it as below (its ADR 0011).

## Decision

- Declare `ACCESS_LOCAL_NETWORK` in the manifest.
- `LocalNetwork.isLocal(url)` treats a server as local when its host ends with `.local` (mDNS, not resolved) or when
  any address the host resolves to is private, loopback, link-local or unique local (10/8, 172.16/12, 192.168/16,
  127/8, 169.254/16, ::1, fc00::/7, fe80::/10) or CGNAT 100.64/10. A host that doesn't resolve is not local.
- `LocalNetwork.isGranted` is true below SDK 37, otherwise `checkSelfPermission`.
- The UI asks with the Activity Result API before "Test connection", on Finish in the onboarding server step and on
  Save in Settings, only when the URL is local and access is missing. The form shows a short explanation while that is
  the case. A denial shows an inline error with a button to the app's settings instead of a timeout; after a "don't ask
  again" denial the app opens its settings page directly.
- The upload job can't ask. Before connecting, a blocked local server fails the job with
  `SyncError.Kind.LOCAL_NETWORK_PERMISSION` (no retry); a connect timeout or refusal is mapped to the same kind when
  the server is local and access is missing. The Brews tab shows the error with "Allow local network", which asks and
  then resends.

## Consequences

- `NEARBY_DEVICES` is the group of the Bluetooth permissions the app already asks for, so a user who allowed
  Bluetooth is usually not prompted again.
- A DNS lookup decides whether to ask; the fallback mapping covers a name that changes to a LAN address later.
- Users with only a public HTTPS server are never asked.
- The local emulator runs API 37, so the prompt and the grant were checked there against the stub at
  `http://10.0.2.2:8080`; enforcement logic is covered by unit tests with injected resolvers and permission checks.
