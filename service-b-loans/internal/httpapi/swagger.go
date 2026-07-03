package httpapi

import (
	"embed"
	"net/http"
)

//go:embed docs/openapi.yaml
var openAPISpec embed.FS

// swaggerUIPage renders Swagger UI via its public CDN bundle, pointed at our
// embedded OpenAPI spec. We hand-author the spec instead of using swaggo/swag's
// code-generation approach because that requires running `swag init` with a local
// Go toolchain, which isn't available in the environment this was written in; this
// gets the same interactive docs without a codegen build step.
const swaggerUIPage = `<!DOCTYPE html>
<html>
<head>
  <title>Loan Service API Docs</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css" />
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script>
    window.onload = () => {
      window.ui = SwaggerUIBundle({
        url: '/swagger/openapi.yaml',
        dom_id: '#swagger-ui',
      });
    };
  </script>
</body>
</html>`

func swaggerUIHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = w.Write([]byte(swaggerUIPage))
}

func openAPISpecHandler(w http.ResponseWriter, r *http.Request) {
	data, err := openAPISpec.ReadFile("docs/openapi.yaml")
	if err != nil {
		http.Error(w, "spec not found", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/yaml")
	_, _ = w.Write(data)
}
