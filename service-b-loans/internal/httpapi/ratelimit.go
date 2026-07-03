package httpapi

import (
	"net/http"

	"golang.org/x/time/rate"
)

// rateLimit is a simple process-wide token bucket (bonus requirement). A global
// limiter is a deliberate simplification over per-IP limiting: this service has no
// end users of its own (only Service A calls it), so throttling total throughput
// is the relevant protection, not per-client fairness.
func rateLimit(requestsPerSecond float64, burst int) func(http.Handler) http.Handler {
	limiter := rate.NewLimiter(rate.Limit(requestsPerSecond), burst)

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if !limiter.Allow() {
				writeJSON(w, http.StatusTooManyRequests, map[string]string{
					"message": "rate limit exceeded, please retry shortly",
				})
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
