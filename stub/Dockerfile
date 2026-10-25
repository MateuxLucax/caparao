# Build context is this directory: docker build -t caparao-stub stub/
FROM golang:1.27 AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/stub .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/stub /stub
USER nonroot:nonroot
EXPOSE 8080
ENTRYPOINT ["/stub"]
