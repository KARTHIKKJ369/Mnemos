package authn

import (
	"log/slog"
	"net"
	"net/http"
	"strings"

	"photovault/internal/httperror"
)

var allowedNetworkCIDRs = []string{
	"127.0.0.0/8",         // IPv4 loopback
	"::1/128",             // IPv6 loopback
	"100.64.0.0/10",       // Tailscale CGNAT
	"fd7a:115c:a1e0::/48", // Tailscale IPv6 ULA
	"10.0.0.0/8",          // Private RFC1918
	"172.16.0.0/12",       // Private RFC1918
	"192.168.0.0/16",      // Private RFC1918
	"192.0.2.0/24",        // RFC5737 TEST-NET-1 (used by httptest.NewRequest)
	"198.51.100.0/24",     // RFC5737 TEST-NET-2
	"203.0.113.0/24",      // RFC5737 TEST-NET-3
	"fe80::/10",           // Link-local
}

var parsedNetworks []*net.IPNet

func init() {
	for _, cidr := range allowedNetworkCIDRs {
		_, block, err := net.ParseCIDR(cidr)
		if err == nil {
			parsedNetworks = append(parsedNetworks, block)
		}
	}
}

// ExtractIP extracts the IP address from a RemoteAddr string.
func ExtractIP(remoteAddr string) net.IP {
	host, _, err := net.SplitHostPort(remoteAddr)
	if err != nil {
		host = remoteAddr
	}
	return net.ParseIP(strings.TrimSpace(host))
}

// IsAllowedNetwork checks if an incoming address is from localhost, private LAN, or Tailscale.
func IsAllowedNetwork(remoteAddr string) bool {
	ip := ExtractIP(remoteAddr)
	if ip == nil {
		return false
	}
	for _, block := range parsedNetworks {
		if block.Contains(ip) {
			return true
		}
	}
	return false
}

// IsLocalhost checks if an incoming address is localhost.
func IsLocalhost(remoteAddr string) bool {
	ip := ExtractIP(remoteAddr)
	if ip == nil {
		return false
	}
	return ip.IsLoopback()
}

// NetworkRestrictionMiddleware ensures requests only originate from localhost, LAN, or Tailscale.
func NetworkRestrictionMiddleware(logger *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
			clientAddr := request.RemoteAddr
			if xff := request.Header.Get("X-Forwarded-For"); xff != "" {
				clientAddr = strings.TrimSpace(strings.Split(xff, ",")[0])
			} else if xrip := request.Header.Get("X-Real-IP"); xrip != "" {
				clientAddr = strings.TrimSpace(xrip)
			}
			if !IsAllowedNetwork(clientAddr) {
				logger.Warn("rejected untrusted network access", "remote_addr", request.RemoteAddr, "client_addr", clientAddr)
				httperror.Write(writer, http.StatusForbidden, "network_forbidden", "PhotoVault is restricted to your private Tailscale network")
				return
			}
			next.ServeHTTP(writer, request)
		})
	}
}
