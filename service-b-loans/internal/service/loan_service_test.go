package service_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/service"
)

// fakeRepo is a hand-written in-memory fake, the idiomatic Go alternative to a
// mocking framework for a small interface like LoanRepository.
type fakeRepo struct {
	loans     map[int64]*domain.Loan
	nextID    int64
	createErr error
	findErr   error
	returnErr error
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{loans: make(map[int64]*domain.Loan), nextID: 1}
}

func (f *fakeRepo) Create(_ context.Context, loan *domain.Loan) error {
	if f.createErr != nil {
		return f.createErr
	}
	loan.ID = f.nextID
	f.nextID++
	stored := *loan
	f.loans[loan.ID] = &stored
	return nil
}

func (f *fakeRepo) FindByID(_ context.Context, id int64) (*domain.Loan, error) {
	if f.findErr != nil {
		return nil, f.findErr
	}
	loan, ok := f.loans[id]
	if !ok {
		return nil, domain.ErrLoanNotFound
	}
	copyOfLoan := *loan
	return &copyOfLoan, nil
}

func (f *fakeRepo) MarkReturned(_ context.Context, id int64, loan *domain.Loan) error {
	if f.returnErr != nil {
		return f.returnErr
	}
	if _, ok := f.loans[id]; !ok {
		return domain.ErrLoanNotFound
	}
	stored := *loan
	f.loans[id] = &stored
	return nil
}

func (f *fakeRepo) FindActiveByUser(_ context.Context, userID int64) ([]domain.Loan, error) {
	var result []domain.Loan
	for _, loan := range f.loans {
		if loan.UserID == userID && loan.Status == domain.StatusActive {
			result = append(result, *loan)
		}
	}
	return result, nil
}

func (f *fakeRepo) FindHistory(_ context.Context, userID *int64) ([]domain.Loan, error) {
	var result []domain.Loan
	for _, loan := range f.loans {
		if userID == nil || loan.UserID == *userID {
			result = append(result, *loan)
		}
	}
	return result, nil
}

// fakeBookChecker simulates Service A's availability responses without any HTTP call.
type fakeBookChecker struct {
	availability *domain.BookAvailability
	err          error
}

func (f *fakeBookChecker) CheckAvailability(_ context.Context, bookID int64) (*domain.BookAvailability, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.availability, nil
}

func TestRegisterLoan_Success(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{availability: &domain.BookAvailability{BookID: 5, Exists: true, AvailableCopies: 2}}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	loan, err := svc.RegisterLoan(context.Background(), 1, 5)

	require.NoError(t, err)
	assert.Equal(t, int64(1), loan.UserID)
	assert.Equal(t, int64(5), loan.BookID)
	assert.Equal(t, domain.StatusActive, loan.Status)
	assert.True(t, loan.DueDate.After(loan.LoanDate))
}

func TestRegisterLoan_BookNotFound(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{availability: &domain.BookAvailability{BookID: 999, Exists: false}}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	_, err := svc.RegisterLoan(context.Background(), 1, 999)

	require.ErrorIs(t, err, domain.ErrBookNotFound)
	assert.Empty(t, repo.loans, "no loan should be persisted when the book doesn't exist")
}

func TestRegisterLoan_BookHasNoAvailableCopies(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{availability: &domain.BookAvailability{BookID: 5, Exists: true, AvailableCopies: 0}}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	_, err := svc.RegisterLoan(context.Background(), 1, 5)

	require.ErrorIs(t, err, domain.ErrBookUnavailable)
	assert.Empty(t, repo.loans)
}

func TestRegisterLoan_LibraryServiceDown(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{err: errors.Join(domain.ErrLibraryServiceDown, errors.New("dial tcp: connection refused"))}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	_, err := svc.RegisterLoan(context.Background(), 1, 5)

	require.ErrorIs(t, err, domain.ErrLibraryServiceDown)
	assert.Empty(t, repo.loans, "must not create a loan it could not validate")
}

func TestReturnLoan_Success(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{availability: &domain.BookAvailability{BookID: 5, Exists: true, AvailableCopies: 2}}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	created, err := svc.RegisterLoan(context.Background(), 1, 5)
	require.NoError(t, err)

	returned, err := svc.ReturnLoan(context.Background(), created.ID)

	require.NoError(t, err)
	assert.Equal(t, domain.StatusReturned, returned.Status)
	require.NotNil(t, returned.ReturnDate)
}

func TestReturnLoan_AlreadyReturned(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{availability: &domain.BookAvailability{BookID: 5, Exists: true, AvailableCopies: 2}}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	created, err := svc.RegisterLoan(context.Background(), 1, 5)
	require.NoError(t, err)
	_, err = svc.ReturnLoan(context.Background(), created.ID)
	require.NoError(t, err)

	_, err = svc.ReturnLoan(context.Background(), created.ID)

	require.ErrorIs(t, err, domain.ErrLoanAlreadyReturned)
}

func TestActiveLoansByUser_FiltersOutReturnedLoans(t *testing.T) {
	repo := newFakeRepo()
	checker := &fakeBookChecker{availability: &domain.BookAvailability{BookID: 5, Exists: true, AvailableCopies: 3}}
	svc := service.NewLoanService(repo, checker, 14*24*time.Hour)

	first, err := svc.RegisterLoan(context.Background(), 1, 5)
	require.NoError(t, err)
	_, err = svc.RegisterLoan(context.Background(), 1, 5)
	require.NoError(t, err)
	_, err = svc.ReturnLoan(context.Background(), first.ID)
	require.NoError(t, err)

	active, err := svc.ActiveLoansByUser(context.Background(), 1)

	require.NoError(t, err)
	assert.Len(t, active, 1)
}
