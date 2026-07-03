// Package config loads configuration from environment variables. Kept deliberately
// tiny (no viper/koanf) since the surface here is a handful of scalars.
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Port                 string
	DatabaseURL          string
	LibraryServiceURL    string
	InternalAPIKey       string
	LibraryClientTimeout time.Duration
	LoanPeriod           time.Duration
}

// Load reads configuration from the environment, applying sane local-dev defaults
// so the service is runnable without a .env file. In docker-compose / production
// every one of these is set explicitly.
func Load() (*Config, error) {
	cfg := &Config{
		Port:              getEnv("SERVER_PORT", "8081"),
		LibraryServiceURL: getEnv("LIBRARY_SERVICE_URL", "http://localhost:8080"),
		InternalAPIKey:    getEnv("INTERNAL_API_KEY", "change-this-internal-key"),
	}

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		host := getEnv("DB_HOST", "localhost")
		port := getEnv("DB_PORT", "5434")
		user := getEnv("DB_USERNAME", "loans_user")
		pass := getEnv("DB_PASSWORD", "loans_pass")
		name := getEnv("DB_NAME", "loans_db")
		dbURL = fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable", user, pass, host, port, name)
	}
	cfg.DatabaseURL = dbURL

	timeoutMs, err := strconv.Atoi(getEnv("LIBRARY_SERVICE_TIMEOUT_MS", "5000"))
	if err != nil {
		return nil, fmt.Errorf("parsing LIBRARY_SERVICE_TIMEOUT_MS: %w", err)
	}
	cfg.LibraryClientTimeout = time.Duration(timeoutMs) * time.Millisecond

	loanDays, err := strconv.Atoi(getEnv("LOAN_PERIOD_DAYS", "14"))
	if err != nil {
		return nil, fmt.Errorf("parsing LOAN_PERIOD_DAYS: %w", err)
	}
	cfg.LoanPeriod = time.Duration(loanDays) * 24 * time.Hour

	return cfg, nil
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok && value != "" {
		return value
	}
	return fallback
}
