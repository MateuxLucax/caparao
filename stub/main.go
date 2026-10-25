// Command stub is a development server for the Caparao upload contract (contract/).
// In-memory only; the real server is caparao-exporter.
package main

import (
	"log"
	"net/http"
	"os"
	"time"
)

func main() {
	logger := log.New(os.Stdout, "", log.LstdFlags)

	token := os.Getenv("AUTH_TOKEN")
	if token == "" {
		logger.Fatal("AUTH_TOKEN is required (example: AUTH_TOKEN=dev-token go run .)")
	}
	addr := os.Getenv("LISTEN_ADDR")
	if addr == "" {
		addr = ":8080"
	}

	srv := &http.Server{
		Addr:              addr,
		Handler:           NewServer(token, logger),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	logger.Printf("listening on %s", addr)
	logger.Fatal(srv.ListenAndServe())
}
