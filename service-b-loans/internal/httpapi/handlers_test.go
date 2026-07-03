package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/httpapi"
)

// fakeLoanService implements httpapi.LoanService for handler-level tests, so these
// run without a database or a real Service A.
type fakeLoanService struct {
	registerFn func(ctx context.Context, userID, bookID int64) (*domain.Loan, error)
}

func (f *fakeLoanService) RegisterLoan(ctx context.Context, userID, bookID int64) (*domain.Loan, error) {
	return f.registerFn(ctx, userID, bookID)
}

func (f *fakeLoanService) ReturnLoan(ctx context.Context, loanID int64) (*domain.Loan, error) {
	return nil, domain.ErrLoanNotFound
}

func (f *fakeLoanService) ActiveLoansByUser(ctx context.Context, userID int64) ([]domain.Loan, error) {
	return []domain.Loan{}, nil
}

func (f *fakeLoanService) History(ctx context.Context, userID *int64) ([]domain.Loan, error) {
	return []domain.Loan{}, nil
}

func TestCreateLoan_ReturnsCreated_OnSuccess(t *testing.T) {
	svc := &fakeLoanService{
		registerFn: func(ctx context.Context, userID, bookID int64) (*domain.Loan, error) {
			return &domain.Loan{ID: 42, UserID: userID, BookID: bookID, Status: domain.StatusActive}, nil
		},
	}
	router := httpapi.NewRouter(httpapi.NewLoanHandler(svc))

	body, _ := json.Marshal(map[string]int64{"userId": 1, "bookId": 5})
	req := httptest.NewRequest(http.MethodPost, "/loans", bytes.NewReader(body))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	require.Equal(t, http.StatusCreated, rec.Code)

	var got map[string]any
	require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &got))
	assert.Equal(t, float64(42), got["id"])
	assert.Equal(t, "ACTIVE", got["status"])
}

func TestCreateLoan_ReturnsConflict_WhenBookUnavailable(t *testing.T) {
	svc := &fakeLoanService{
		registerFn: func(ctx context.Context, userID, bookID int64) (*domain.Loan, error) {
			return nil, domain.ErrBookUnavailable
		},
	}
	router := httpapi.NewRouter(httpapi.NewLoanHandler(svc))

	body, _ := json.Marshal(map[string]int64{"userId": 1, "bookId": 5})
	req := httptest.NewRequest(http.MethodPost, "/loans", bytes.NewReader(body))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusConflict, rec.Code)
}

func TestCreateLoan_ReturnsBadGateway_WhenLibraryServiceDown(t *testing.T) {
	svc := &fakeLoanService{
		registerFn: func(ctx context.Context, userID, bookID int64) (*domain.Loan, error) {
			return nil, domain.ErrLibraryServiceDown
		},
	}
	router := httpapi.NewRouter(httpapi.NewLoanHandler(svc))

	body, _ := json.Marshal(map[string]int64{"userId": 1, "bookId": 5})
	req := httptest.NewRequest(http.MethodPost, "/loans", bytes.NewReader(body))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadGateway, rec.Code)
}

func TestHealth_ReturnsUp(t *testing.T) {
	router := httpapi.NewRouter(httpapi.NewLoanHandler(&fakeLoanService{}))

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	var got map[string]string
	require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &got))
	assert.Equal(t, "UP", got["status"])
}
