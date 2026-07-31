package httpapi

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"photovault/internal/authn"
	"photovault/internal/httperror"
	"photovault/internal/vaults"
)

type VaultService interface {
	Create(context.Context, string, string) (vaults.Vault, error)
}
type VaultHandler struct{ service VaultService }

func NewVaultHandler(service VaultService) *VaultHandler                 { return &VaultHandler{service} }
func (h *VaultHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) { http.NotFound(w, r) }
func (h *VaultHandler) Create(w http.ResponseWriter, r *http.Request) {
	device, ok := authn.DeviceFromContext(r.Context())
	if !ok {
		httperror.Write(w, 401, "unauthorized", "authentication failed")
		return
	}
	var input struct {
		Type string `json:"type"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httperror.Write(w, 400, "invalid_request", "request body must be valid JSON")
		return
	}
	v, err := h.service.Create(r.Context(), device.ID, input.Type)
	if err != nil {
		httperror.Write(w, 400, "invalid_request", "type must be legacy or encrypted")
		return
	}
	response := map[string]any{"vault_id": v.ID, "type": v.Type}
	if v.Type == "encrypted" {
		response["salt"] = base64.RawURLEncoding.EncodeToString(v.Salt)
		response["argon2"] = map[string]any{"time": v.Params.Time, "memory_kib": v.Params.Memory, "threads": v.Params.Threads}
		response["algorithm_version"] = v.AlgorithmVersion
	}
	writeJSON(w, 201, response)
}
