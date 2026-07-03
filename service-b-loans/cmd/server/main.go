// Command server starts the loans HTTP service: it loads configuration, connects
// to Postgres, applies migrations, and serves the loans API until interrupted.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/KarenC7/library-management-system/service-b-loans/internal/config"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/httpapi"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/libraryclient"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/migrate"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/repository"
	"github.com/KarenC7/library-management-system/service-b-loans/internal/service"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))

	if err := run(); err != nil {
		slog.Error("service exited with error", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	if err := migrate.Run(cfg.DatabaseURL); err != nil {
		return err
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	pool, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	pingCtx, pingCancel := context.WithTimeout(ctx, 5*time.Second)
	defer pingCancel()
	if err := pool.Ping(pingCtx); err != nil {
		return err
	}

	loanRepo := repository.NewPostgresLoanRepository(pool)
	bookChecker := libraryclient.New(cfg.LibraryServiceURL, cfg.InternalAPIKey, cfg.LibraryClientTimeout)
	loanService := service.NewLoanService(loanRepo, bookChecker, cfg.LoanPeriod)
	loanHandler := httpapi.NewLoanHandler(loanService)
	router := httpapi.NewRouter(loanHandler)

	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	serverErrors := make(chan error, 1)
	go func() {
		slog.Info("loan service listening", "port", cfg.Port)
		serverErrors <- server.ListenAndServe()
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	select {
	case err := <-serverErrors:
		if !errors.Is(err, http.ErrServerClosed) {
			return err
		}
		return nil
	case sig := <-stop:
		slog.Info("shutting down", "signal", sig.String())
	}

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	return server.Shutdown(shutdownCtx)
}
