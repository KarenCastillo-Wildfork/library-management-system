package httpapi

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

// errorResponse mirrors the shape returned by Service A's GlobalExceptionHandler,
// so a client talking to either service sees the same error contract.
type errorResponse struct {
	Timestamp time.Time `json:"timestamp"`
	Status    int       `json:"status"`
	Error     string    `json:"error"`
	Message   string    `json:"message"`
	Path      string    `json:"path"`
}

// writeError maps a domain/business error to the right HTTP status and writes a
// consistent JSON body. This is the single place status-code decisions are made.
func writeError(w http.ResponseWriter, r *http.Request, err error) {
	status, message := classify(err)
	if status >= http.StatusInternalServerError {
		slog.Error("request failed", "path", r.URL.Path, "error", err)
	} else {
		slog.Warn("request rejected", "path", r.URL.Path, "status", status, "error", err)
	}
	writeJSON(w, status, errorResponse{
		Timestamp: time.Now().UTC(),
		Status:    status,
		Error:     http.StatusText(status),
		Message:   message,
		Path:      r.URL.Path,
	})
}

func classify(err error) (int, string) {
	switch {
	case errors.Is(err, domain.ErrInvalidInput):
		return http.StatusBadRequest, err.Error()
	case errors.Is(err, domain.ErrBookNotFound):
		return http.StatusNotFound, err.Error()
	case errors.Is(err, domain.ErrLoanNotFound):
		return http.StatusNotFound, err.Error()
	case errors.Is(err, domain.ErrBookUnavailable):
		return http.StatusConflict, err.Error()
	case errors.Is(err, domain.ErrLoanAlreadyReturned):
		return http.StatusConflict, err.Error()
	case errors.Is(err, domain.ErrLibraryServiceDown):
		return http.StatusBadGateway, "Could not reach the library service to validate the book"
	default:
		return http.StatusInternalServerError, "An unexpected error occurred"
	}
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		slog.Error("failed to encode JSON response", "error", err)
	}
}
