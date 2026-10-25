# Sync (stage 5)

Automated: `scripts/check.sh` (`ContractTest` and the stub's `go test` on the same `contract/` files,
`ExporterClientTest` against a local HTTP server, `UploadSchedulerTest`, `UploadRunnerTest`, `ServerUrlTest`,
`LocalNetworkTest`, `ServerViewModelTest`, `BrewsViewModelTest`) and, on the emulator, `AppTest` (onboarding through the
server step).

Manual, on a phone with the Timemore Dot and a computer on the same Wi-Fi. Run the stub on the computer
(`cd stub && AUTH_TOKEN=dev-token go run .`) and note the computer's LAN address, for example `192.0.2.10`. Never
write a real address or token in a record below.

1. Fresh install, onboarding: after the scale step, the server step shows URL, token and Test connection. Type
   `http://<computer>:8080` and token `dev-token`. Test connection asks for nearby devices / local network only if not
   allowed yet, then shows "Connected". A wrong token shows "The server refused the token". Finish.
2. Settings shows the saved server. Change the URL to `http://coffee.example.com`: the cleartext warning shows. Put the
   real one back; Save is enabled only while the fields differ from the saved ones.
3. Record an espresso. Within a minute the stub logs `POST "/api/v1/brews" 201` with the brew's id; the Brews tab shows
   "Last sync HH:MM" and the brew "Synced HH:MM".
4. Airplane mode on, record a filter brew: it shows "Only on this phone". Airplane mode off: it uploads by itself
   (stub log, "Synced" in the list) without opening the app.
5. Stop the stub, record a brew, start the stub again: the brew uploads after the backoff. In Settings, Save the same
   server again: the stub logs `200` for brews resent, and `curl -s http://localhost:8080/api/v1/brews -H
   "Authorization: Bearer dev-token"` lists each brew once.
6. Restart the stub with another token: the next brew shows "The server refused the token" in the Brews tab with
   "Server settings"; fixing the token and saving uploads it.
7. Deny the local network permission in the app's settings, record a brew: the Brews tab shows the blocked local
   network error and "Allow local network"; allowing it uploads the brew.
8. Remove server in Settings: the Brews tab says no server is set, and new brews stay on the phone (no request in the
   stub log).

## Runs

No device run yet. The emulator (API 37) uploaded a replayed espresso to the stub at `http://10.0.2.2:8080` (201) after
allowing local network access from Test connection (2026-10-25).
