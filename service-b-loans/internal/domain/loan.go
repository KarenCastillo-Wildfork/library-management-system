// Package domain holds the core loan model and business errors, free of any
// framework, HTTP, or database dependency so it can be tested and reasoned about
// in isolation.
package domain

import (
	"errors"
	"time"
)

// Status is the lifecycle state of a Loan.
type Status string

const (
	StatusActive   Status = "ACTIVE"
	StatusReturned Status = "RETURNED"
)

// Loan is a single book checkout, owned exclusively by this service.
type Loan struct {
	ID         int64
	UserID     int64
	BookID     int64
	LoanDate   time.Time
	DueDate    time.Time
	ReturnDate *time.Time
	Status     Status
}

// BookAvailability is what Service A reports back when asked whether a book can be
// borrowed. Exists=false is a normal, expected outcome (not an error) so callers can
// tell "book doesn't exist" apart from "Service A is unreachable".
type BookAvailability struct {
	BookID          int64
	Exists          bool
	AvailableCopies int
}

// Sentinel errors. Handlers map these to HTTP status codes; nothing in this
// service panics for an expected business outcome.
var (
	ErrBookNotFound        = errors.New("book not found")
	ErrBookUnavailable     = errors.New("book has no available copies")
	ErrLoanNotFound        = errors.New("loan not found")
	ErrLoanAlreadyReturned = errors.New("loan already returned")
	ErrLibraryServiceDown  = errors.New("library service (service A) is unavailable")
	ErrInvalidInput        = errors.New("invalid input")
)
