package httpapi

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

// NewRouter wires all routes for the loans service. chi was chosen over Gin/Echo
// because it stays on the standard library's http.Handler/http.HandlerFunc types
// (no custom Context type, no reflection-based binding) - a thin, idiomatic layer
// over net/http rather than a framework with its own conventions.
func NewRouter(loanHandler *LoanHandler) http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.Recoverer) // last-resort safety net; business logic never panics on purpose
	r.Use(requestLogger)
	r.Use(rateLimit(20, 40))

	r.Get("/health", Health)

	r.Get("/swagger/index.html", swaggerUIHandler)
	r.Get("/swagger/openapi.yaml", openAPISpecHandler)
	r.Get("/swagger/", swaggerUIHandler)

	r.Route("/loans", func(r chi.Router) {
		r.Post("/", loanHandler.CreateLoan)
		r.Post("/{id}/return", loanHandler.ReturnLoan)
		r.Get("/active", loanHandler.ActiveLoans)
		r.Get("/history", loanHandler.History)
	})

	return r
}
