// Package httperror writes consistent JSON API errors.
package httperror

import (
	"encoding/json"
	"net/http"
)

// Write sends a JSON API error response.
func Write(writer http.ResponseWriter, status int, code, message string) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(map[string]map[string]string{
		"error": {"code": code, "message": message},
	})
}
