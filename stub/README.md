# Stub server

A development server for the [upload contract](../contract/README.md): standard library only, brews kept in memory
(lost on restart). For development and the end-to-end test; the real server is `caparao-exporter`
([ADR 0014](../docs/adr/0014-stub-in-repo-exporter-separate.md)).

## Run

```bash
cd stub
AUTH_TOKEN=dev-token go run .
```

| Variable | Meaning | Default |
|----------|---------|---------|
| `AUTH_TOKEN` | Bearer token clients must send. Required: the stub refuses to start without it | none |
| `LISTEN_ADDR` | Address to listen on | `:8080` |

## Endpoints

| Request | Response |
|---------|----------|
| `POST /api/v1/brews` | `201` new, `200` existing id (replaced), `400` `{"error": "..."}`, `401`, `413` over 4 MiB |
| `GET /api/v1/brews` | Stored brews, newest first, without samples but with `sample_count` |
| `GET /healthz` | `200 ok`, no auth |

```bash
TOKEN=dev-token  # the AUTH_TOKEN the stub runs with
curl -i -X POST http://localhost:8080/api/v1/brews \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data @../contract/valid/espresso.json

curl -s http://localhost:8080/api/v1/brews -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/healthz
```

Each request is logged as one line (method, path, status, brew id). The token is never logged.

## From the Android emulator

The emulator reaches the host machine at `http://10.0.2.2:8080` (use that as the server URL, with token `dev-token`).
A physical phone needs your computer's LAN address instead, for example `http://192.0.2.10:8080`.

## Docker

```bash
docker build -t caparao-stub stub/
docker run --rm -p 8080:8080 -e AUTH_TOKEN=dev-token caparao-stub
```

## Tests

```bash
go vet ./... && go test ./...
```

The tests post every file in `../contract/valid` (`201`, then `200` on resend) and `../contract/invalid` (`400`).
