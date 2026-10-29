package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"sort"
	"strings"
	"sync"
)

const maxBodyBytes = 4 << 20 // 4 MiB

// Server is the in-memory stub. Create it with NewServer.
type Server struct {
	tokenHash [sha256.Size]byte
	logger    *log.Logger

	mu    sync.RWMutex
	brews map[string]*Brew
}

// NewServer returns a handler that requires "Authorization: Bearer <token>" on the /api routes.
func NewServer(token string, logger *log.Logger) *Server {
	return &Server{
		tokenHash: sha256.Sum256([]byte(token)),
		logger:    logger,
		brews:     make(map[string]*Brew),
	}
}

type ctxKey struct{}

// reqInfo lets handlers report the brew id to the access log.
type reqInfo struct{ id string }

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}

// ServeHTTP routes, logs one line per request (never the Authorization header).
func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	info := &reqInfo{}
	sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}
	r = r.WithContext(context.WithValue(r.Context(), ctxKey{}, info))

	switch r.URL.Path {
	case "/healthz":
		s.healthz(sw, r)
	case "/api/v1/brews":
		s.brewsRoute(sw, r)
	default:
		if id, ok := strings.CutPrefix(r.URL.Path, "/api/v1/brews/"); ok {
			s.brewRoute(sw, r, id)
		} else {
			writeError(sw, http.StatusNotFound, "not found")
		}
	}

	id := info.id
	if id == "" {
		id = "-"
	}
	s.logger.Printf("%s %q %d id=%s", r.Method, r.URL.Path, sw.status, id)
}

func (s *Server) healthz(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		w.Header().Set("Allow", "GET, HEAD")
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = io.WriteString(w, "ok")
}

func (s *Server) brewsRoute(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		w.Header().Set("Allow", "GET, POST")
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.authorized(r) {
		w.Header().Set("WWW-Authenticate", "Bearer")
		writeError(w, http.StatusUnauthorized, "missing or invalid token")
		return
	}
	if r.Method == http.MethodPost {
		s.postBrew(w, r)
	} else {
		s.listBrews(w)
	}
}

// brewRoute serves GET /api/v1/brews/{id}: the brew in the upload format, samples included.
func (s *Server) brewRoute(w http.ResponseWriter, r *http.Request, id string) {
	if r.Method != http.MethodGet {
		w.Header().Set("Allow", "GET")
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.authorized(r) {
		w.Header().Set("WWW-Authenticate", "Bearer")
		writeError(w, http.StatusUnauthorized, "missing or invalid token")
		return
	}
	if !uuidRe.MatchString(id) {
		writeError(w, http.StatusNotFound, "brew not found")
		return
	}
	if info, ok := r.Context().Value(ctxKey{}).(*reqInfo); ok {
		info.id = id // validated UUID, safe to log
	}
	s.mu.RLock()
	b, ok := s.brews[id]
	s.mu.RUnlock()
	if !ok {
		writeError(w, http.StatusNotFound, "brew not found")
		return
	}
	writeJSON(w, http.StatusOK, b)
}

func (s *Server) authorized(r *http.Request) bool {
	const prefix = "Bearer "
	h := r.Header.Get("Authorization")
	if len(h) < len(prefix) || !strings.EqualFold(h[:len(prefix)], prefix) {
		return false
	}
	got := sha256.Sum256([]byte(h[len(prefix):]))
	return subtle.ConstantTimeCompare(got[:], s.tokenHash[:]) == 1
}

func (s *Server) postBrew(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxBodyBytes))
	if err != nil {
		var tooBig *http.MaxBytesError
		if errors.As(err, &tooBig) {
			writeError(w, http.StatusRequestEntityTooLarge, "body larger than 4 MiB")
		} else {
			writeError(w, http.StatusBadRequest, "could not read body")
		}
		return
	}
	brew, err := parseBrew(body)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if info, ok := r.Context().Value(ctxKey{}).(*reqInfo); ok {
		info.id = brew.ID // validated UUID, safe to log
	}

	s.mu.Lock()
	_, existed := s.brews[brew.ID]
	s.brews[brew.ID] = brew
	s.mu.Unlock()

	status := http.StatusCreated
	if existed {
		status = http.StatusOK
	}
	writeJSON(w, status, map[string]string{"id": brew.ID})
}

type summary struct {
	ID               string   `json:"id"`
	Mode             string   `json:"mode"`
	StartedAtMs      int64    `json:"started_at_ms"`
	EndedAtMs        int64    `json:"ended_at_ms"`
	DoseG            float64  `json:"dose_g"`
	YieldG           float64  `json:"yield_g"`
	TimeToFirstDropS *float64 `json:"time_to_first_drop_s"`
	FlowAvgGps       float64  `json:"flow_avg_gps"`
	FlowPeakGps      float64  `json:"flow_peak_gps"`
	Interrupted      bool     `json:"interrupted"`
	SampleCount      int      `json:"sample_count"`
}

func (s *Server) listBrews(w http.ResponseWriter) {
	s.mu.RLock()
	out := make([]summary, 0, len(s.brews))
	for _, b := range s.brews {
		out = append(out, summary{
			ID: b.ID, Mode: b.Mode, StartedAtMs: b.StartedAtMs, EndedAtMs: b.EndedAtMs,
			DoseG: b.DoseG, YieldG: b.YieldG, TimeToFirstDropS: b.TimeToFirstDropS,
			FlowAvgGps: b.FlowAvgGps, FlowPeakGps: b.FlowPeakGps, Interrupted: b.Interrupted,
			SampleCount: len(b.Samples),
		})
	}
	s.mu.RUnlock()
	sort.Slice(out, func(i, j int) bool {
		if out[i].StartedAtMs != out[j].StartedAtMs {
			return out[i].StartedAtMs > out[j].StartedAtMs
		}
		return out[i].ID < out[j].ID
	})
	writeJSON(w, http.StatusOK, out)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
