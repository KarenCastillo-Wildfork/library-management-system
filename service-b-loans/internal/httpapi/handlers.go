package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

// LoanService is the subset of service.LoanService this HTTP layer needs. Defining
// it here (rather than importing the concrete type) keeps the handlers testable
// with a fake and avoids a hard dependency on the service package's internals.
type LoanService interface {
	RegisterLoan(ctx context.Context, userID, bookID int64) (*domain.Loan, error)
	ReturnLoan(ctx context.Context, loanID int64) (*domain.Loan, error)
	ActiveLoansByUser(ctx context.Context, userID int64) ([]domain.Loan, error)
	History(ctx context.Context, userID *int64) ([]domain.Loan, error)
}

type LoanHandler struct {
	service LoanService
}

func NewLoanHandler(service LoanService) *LoanHandler {
	return &LoanHandler{service: service}
}

// CreateLoan godoc
// @Summary      Register a loan
// @Description  Validates the book with Service A, then persists the loan.
// @Tags         loans
// @Accept       json
// @Produce      json
// @Param        request body createLoanRequest true "userId and bookId"
// @Success      201 {object} loanResponse
// @Failure      400 {object} errorResponse
// @Failure      404 {object} errorResponse
// @Failure      409 {object} errorResponse
// @Failure      502 {object} errorResponse
// @Router       /loans [post]
func (h *LoanHandler) CreateLoan(w http.ResponseWriter, r *http.Request) {
	var req createLoanRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, r, errBadJSON(err))
		return
	}

	loan, err := h.service.RegisterLoan(r.Context(), req.UserID, req.BookID)
	if err != nil {
		writeError(w, r, err)
		return
	}

	writeJSON(w, http.StatusCreated, toLoanResponse(loan))
}

// ReturnLoan godoc
// @Summary      Return a loan
// @Tags         loans
// @Produce      json
// @Param        id path int true "Loan ID"
// @Success      200 {object} loanResponse
// @Failure      404 {object} errorResponse
// @Failure      409 {object} errorResponse
// @Router       /loans/{id}/return [post]
func (h *LoanHandler) ReturnLoan(w http.ResponseWriter, r *http.Request) {
	id, err := parseIDParam(r, "id")
	if err != nil {
		writeError(w, r, err)
		return
	}

	loan, err := h.service.ReturnLoan(r.Context(), id)
	if err != nil {
		writeError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, toLoanResponse(loan))
}

// ActiveLoans godoc
// @Summary      List a user's active loans
// @Tags         loans
// @Produce      json
// @Param        userId query int true "User ID"
// @Success      200 {array} loanResponse
// @Router       /loans/active [get]
func (h *LoanHandler) ActiveLoans(w http.ResponseWriter, r *http.Request) {
	userID, err := parseIDQueryParam(r, "userId", true)
	if err != nil {
		writeError(w, r, err)
		return
	}

	loans, err := h.service.ActiveLoansByUser(r.Context(), *userID)
	if err != nil {
		writeError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, toLoanResponses(loans))
}

// History godoc
// @Summary      List loan history, optionally filtered by user
// @Tags         loans
// @Produce      json
// @Param        userId query int false "User ID (omit for every user's history)"
// @Success      200 {array} loanResponse
// @Router       /loans/history [get]
func (h *LoanHandler) History(w http.ResponseWriter, r *http.Request) {
	userID, err := parseIDQueryParam(r, "userId", false)
	if err != nil {
		writeError(w, r, err)
		return
	}

	loans, err := h.service.History(r.Context(), userID)
	if err != nil {
		writeError(w, r, err)
		return
	}

	writeJSON(w, http.StatusOK, toLoanResponses(loans))
}

// Health godoc
// @Summary      Health check
// @Tags         health
// @Produce      json
// @Success      200 {object} map[string]string
// @Router       /health [get]
func Health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{
		"status": "UP",
		"time":   time.Now().UTC().Format(time.RFC3339),
	})
}

func errBadJSON(cause error) error {
	return errors.Join(domain.ErrInvalidInput, cause)
}

func parseIDParam(r *http.Request, name string) (int64, error) {
	raw := chi.URLParam(r, name)
	id, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return 0, errors.Join(domain.ErrInvalidInput, err)
	}
	return id, nil
}

// parseIDQueryParam reads an int64 query parameter. If required and missing, it
// returns domain.ErrInvalidInput; if optional and missing, it returns (nil, nil).
func parseIDQueryParam(r *http.Request, name string, required bool) (*int64, error) {
	raw := r.URL.Query().Get(name)
	if raw == "" {
		if required {
			return nil, errors.Join(domain.ErrInvalidInput, errors.New(name+" query parameter is required"))
		}
		return nil, nil
	}
	id, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return nil, errors.Join(domain.ErrInvalidInput, err)
	}
	return &id, nil
}
