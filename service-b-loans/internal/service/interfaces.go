package service

import (
	"context"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

// LoanRepository persists loans. Defined here (the consumer), implemented in
// package repository, so LoanService depends only on this abstraction and is
// trivially testable with a fake.
type LoanRepository interface {
	Create(ctx context.Context, loan *domain.Loan) error
	FindByID(ctx context.Context, id int64) (*domain.Loan, error)
	MarkReturned(ctx context.Context, id int64, loan *domain.Loan) error
	FindActiveByUser(ctx context.Context, userID int64) ([]domain.Loan, error)
	// FindHistory returns every loan for userID, or for all users when userID is nil.
	FindHistory(ctx context.Context, userID *int64) ([]domain.Loan, error)
}

// BookChecker validates book existence/availability against Service A.
type BookChecker interface {
	CheckAvailability(ctx context.Context, bookID int64) (*domain.BookAvailability, error)
}
