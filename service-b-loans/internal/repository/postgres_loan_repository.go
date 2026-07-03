// Package repository implements persistence for the loans service using pgx
// directly (database/sql-compatible) with hand-written SQL instead of an ORM: for a
// handful of straightforward queries, plain SQL keeps the generated queries
// obvious and makes error handling explicit rather than hidden behind ORM magic.
package repository

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/domain"
)

type PostgresLoanRepository struct {
	pool *pgxpool.Pool
}

func NewPostgresLoanRepository(pool *pgxpool.Pool) *PostgresLoanRepository {
	return &PostgresLoanRepository{pool: pool}
}

func (r *PostgresLoanRepository) Create(ctx context.Context, loan *domain.Loan) error {
	const query = `
		INSERT INTO loans (user_id, book_id, loan_date, due_date, status)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id`

	err := r.pool.QueryRow(ctx, query, loan.UserID, loan.BookID, loan.LoanDate, loan.DueDate, loan.Status).
		Scan(&loan.ID)
	if err != nil {
		return fmt.Errorf("inserting loan: %w", err)
	}
	return nil
}

func (r *PostgresLoanRepository) FindByID(ctx context.Context, id int64) (*domain.Loan, error) {
	const query = `
		SELECT id, user_id, book_id, loan_date, due_date, return_date, status
		FROM loans WHERE id = $1`

	loan, err := scanLoan(r.pool.QueryRow(ctx, query, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, domain.ErrLoanNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("finding loan %d: %w", id, err)
	}
	return loan, nil
}

func (r *PostgresLoanRepository) MarkReturned(ctx context.Context, id int64, loan *domain.Loan) error {
	const query = `
		UPDATE loans SET return_date = $1, status = $2
		WHERE id = $3`

	tag, err := r.pool.Exec(ctx, query, loan.ReturnDate, loan.Status, id)
	if err != nil {
		return fmt.Errorf("updating loan %d: %w", id, err)
	}
	if tag.RowsAffected() == 0 {
		return domain.ErrLoanNotFound
	}
	return nil
}

func (r *PostgresLoanRepository) FindActiveByUser(ctx context.Context, userID int64) ([]domain.Loan, error) {
	const query = `
		SELECT id, user_id, book_id, loan_date, due_date, return_date, status
		FROM loans WHERE user_id = $1 AND status = $2
		ORDER BY loan_date DESC`

	rows, err := r.pool.Query(ctx, query, userID, domain.StatusActive)
	if err != nil {
		return nil, fmt.Errorf("listing active loans for user %d: %w", userID, err)
	}
	defer rows.Close()

	return collectLoans(rows)
}

func (r *PostgresLoanRepository) FindHistory(ctx context.Context, userID *int64) ([]domain.Loan, error) {
	const query = `
		SELECT id, user_id, book_id, loan_date, due_date, return_date, status
		FROM loans
		WHERE ($1::bigint IS NULL OR user_id = $1)
		ORDER BY loan_date DESC`

	rows, err := r.pool.Query(ctx, query, userID)
	if err != nil {
		return nil, fmt.Errorf("listing loan history: %w", err)
	}
	defer rows.Close()

	return collectLoans(rows)
}

func scanLoan(row pgx.Row) (*domain.Loan, error) {
	var loan domain.Loan
	err := row.Scan(&loan.ID, &loan.UserID, &loan.BookID, &loan.LoanDate, &loan.DueDate, &loan.ReturnDate, &loan.Status)
	if err != nil {
		return nil, err
	}
	return &loan, nil
}

func collectLoans(rows pgx.Rows) ([]domain.Loan, error) {
	loans := make([]domain.Loan, 0)
	for rows.Next() {
		loan, err := scanLoan(rows)
		if err != nil {
			return nil, fmt.Errorf("scanning loan row: %w", err)
		}
		loans = append(loans, *loan)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterating loan rows: %w", err)
	}
	return loans, nil
}
