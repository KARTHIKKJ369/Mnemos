package httpapi

import (
	"crypto/subtle"
	"net"
	"net/http"
	"strings"
)

// AuthBootstrap returns auto-login credentials when the request originates
// from the local loopback interface only, or checks if the bearer token is the admin.
// This lets the server host auto-authenticate without a manual token entry,
// while Tailscale clients (100.64.x.x) and other devices must use a device token.
func AuthBootstrap(admin AdminProvider) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		registration, ok := admin.AdminRegistration()
		if !ok {
			writeJSON(writer, http.StatusOK, map[string]any{
				"is_admin": false,
			})
			return
		}

		// If authenticated with the admin token, report is_admin: true
		authHeader := request.Header.Get("Authorization")
		token := strings.TrimPrefix(authHeader, "Bearer ")
		token = strings.TrimSpace(token)
		if token != "" && subtle.ConstantTimeCompare([]byte(token), []byte(registration.Token)) == 1 {
			writeJSON(writer, http.StatusOK, map[string]any{
				"is_admin":    true,
				"device_id":   registration.Device.ID,
				"device_name": registration.Device.Name,
			})
			return
		}

		clientHost, _, err := net.SplitHostPort(request.RemoteAddr)
		if err != nil {
			clientHost = request.RemoteAddr
		}
		if xff := request.Header.Get("X-Forwarded-For"); xff != "" {
			parts := strings.Split(xff, ",")
			clientHost = strings.TrimSpace(parts[0])
		} else if xrip := request.Header.Get("X-Real-IP"); xrip != "" {
			clientHost = strings.TrimSpace(xrip)
		}
		if h, _, err := net.SplitHostPort(clientHost); err == nil {
			clientHost = h
		}
		ip := net.ParseIP(strings.TrimSpace(clientHost))
		isLoopback := ip != nil && ip.IsLoopback()

		if !isLoopback {
			writeJSON(writer, http.StatusOK, map[string]any{
				"is_admin": false,
			})
			return
		}

		writeJSON(writer, http.StatusOK, map[string]any{
			"is_admin":    true,
			"auth_token":  registration.Token,
			"device_id":   registration.Device.ID,
			"device_name": registration.Device.Name,
		})
	}
}

// isLocalOrTailscale reports whether remoteAddr is loopback or in private/Tailscale ranges.
// Used by network restriction middleware.
func isLocalOrTailscale(remoteAddr string) bool {
	host := remoteAddr
	if h, _, err := net.SplitHostPort(remoteAddr); err == nil {
		host = h
	}
	ip := net.ParseIP(strings.TrimSpace(host))
	if ip == nil {
		return false
	}
	if ip.IsLoopback() {
		return true
	}
	private := []string{
		"10.0.0.0/8",
		"172.16.0.0/12",
		"192.168.0.0/16",
		"100.64.0.0/10", // Tailscale CGNAT
		"fc00::/7",      // IPv6 ULA
	}
	for _, cidr := range private {
		_, network, err := net.ParseCIDR(cidr)
		if err != nil {
			continue
		}
		if network.Contains(ip) {
			return true
		}
	}
	return false
}
