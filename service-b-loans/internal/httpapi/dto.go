package httpapi

import (
	"time"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

// createLoanRequest is the body for POST /loans, sent by Service A.
type createLoanRequest struct {
	UserID int64 `json:"userId"`
	BookID int64 `json:"bookId"`
}

// loanResponse is the JSON shape returned for a loan. Field names are camelCase to
// match exactly what Service A's LoanDto expects when deserializing our responses.
type loanResponse struct {
	ID         int64      `json:"id"`
	UserID     int64      `json:"userId"`
	BookID     int64      `json:"bookId"`
	LoanDate   time.Time  `json:"loanDate"`
	DueDate    time.Time  `json:"dueDate"`
	ReturnDate *time.Time `json:"returnDate,omitempty"`
	Status     string     `json:"status"`
}

func toLoanResponse(loan *domain.Loan) loanResponse {
	return loanResponse{
		ID:         loan.ID,
		UserID:     loan.UserID,
		BookID:     loan.BookID,
		LoanDate:   loan.LoanDate,
		DueDate:    loan.DueDate,
		ReturnDate: loan.ReturnDate,
		Status:     string(loan.Status),
	}
}

func toLoanResponses(loans []domain.Loan) []loanResponse {
	responses := make([]loanResponse, 0, len(loans))
	for i := range loans {
		responses = append(responses, toLoanResponse(&loans[i]))
	}
	return responses
}
