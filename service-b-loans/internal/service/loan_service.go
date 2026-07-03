// Package service contains the business logic for registering, returning and
// listing loans, independent of HTTP or SQL concerns.
package service

import (
	"context"
	"fmt"
	"time"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

type LoanService struct {
	repo        LoanRepository
	bookChecker BookChecker
	loanPeriod  time.Duration
	now         func() time.Time
}

func NewLoanService(repo LoanRepository, bookChecker BookChecker, loanPeriod time.Duration) *LoanService {
	return &LoanService{
		repo:        repo,
		bookChecker: bookChecker,
		loanPeriod:  loanPeriod,
		now:         time.Now,
	}
}

// RegisterLoan validates the book with Service A before persisting anything: this
// is the "Servicio B valida con el Servicio A" requirement, enforced here rather
// than trusting whatever Service A's orchestration call implies.
func (s *LoanService) RegisterLoan(ctx context.Context, userID, bookID int64) (*domain.Loan, error) {
	if userID <= 0 || bookID <= 0 {
		return nil, fmt.Errorf("%w: userId and bookId must be positive", domain.ErrInvalidInput)
	}

	availability, err := s.bookChecker.CheckAvailability(ctx, bookID)
	if err != nil {
		return nil, err // already wrapped as domain.ErrLibraryServiceDown by the client
	}
	if !availability.Exists {
		return nil, domain.ErrBookNotFound
	}
	if availability.AvailableCopies <= 0 {
		return nil, domain.ErrBookUnavailable
	}

	loanDate := s.now().UTC()
	loan := &domain.Loan{
		UserID:   userID,
		BookID:   bookID,
		LoanDate: loanDate,
		DueDate:  loanDate.Add(s.loanPeriod),
		Status:   domain.StatusActive,
	}

	if err := s.repo.Create(ctx, loan); err != nil {
		return nil, fmt.Errorf("persisting loan: %w", err)
	}

	return loan, nil
}

// ReturnLoan marks an active loan as returned. Returning an already-returned loan
// is treated as a client error (domain.ErrLoanAlreadyReturned), not silently
// accepted, so double-return bugs on the caller's side are visible.
func (s *LoanService) ReturnLoan(ctx context.Context, loanID int64) (*domain.Loan, error) {
	loan, err := s.repo.FindByID(ctx, loanID)
	if err != nil {
		return nil, err
	}
	if loan.Status == domain.StatusReturned {
		return nil, domain.ErrLoanAlreadyReturned
	}

	returnDate := s.now().UTC()
	loan.ReturnDate = &returnDate
	loan.Status = domain.StatusReturned

	if err := s.repo.MarkReturned(ctx, loanID, loan); err != nil {
		return nil, fmt.Errorf("marking loan %d returned: %w", loanID, err)
	}

	return loan, nil
}

func (s *LoanService) ActiveLoansByUser(ctx context.Context, userID int64) ([]domain.Loan, error) {
	return s.repo.FindActiveByUser(ctx, userID)
}

// History returns all loans for userID, or for every user when userID is nil
// (an ADMIN request with no filter).
func (s *LoanService) History(ctx context.Context, userID *int64) ([]domain.Loan, error) {
	return s.repo.FindHistory(ctx, userID)
}
