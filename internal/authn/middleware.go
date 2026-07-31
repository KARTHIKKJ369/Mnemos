// Package authn provides device-token HTTP authentication.
package authn

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"strings"

	"photovault/internal/devices"
	"photovault/internal/httperror"
)

type contextKey struct{}

// Authenticator validates device bearer tokens.
type Authenticator interface {
	Authenticate(ctx context.Context, token string) (devices.Device, error)
}

// Middleware authenticates requests using an Authorization Bearer device token.
func Middleware(logger *slog.Logger, authenticator Authenticator) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
			remoteIP := requestRemoteIP(request)
			userAgent := request.UserAgent()
			token, ok := bearerToken(request.Header.Get("Authorization"))
			if !ok {
				logAuthentication(logger, "", "", remoteIP, userAgent, false)
				writeUnauthorized(writer)
				return
			}
			device, err := authenticator.Authenticate(request.Context(), token)
			if err != nil {
				if errors.Is(err, devices.ErrInvalidToken) {
					logAuthentication(logger, "", "", remoteIP, userAgent, false)
					writeUnauthorized(writer)
					return
				}
				logAuthentication(logger, "", "", remoteIP, userAgent, false)
				writeInternalError(writer)
				return
			}
			logAuthentication(logger, device.ID, device.Name, remoteIP, userAgent, true)
			next.ServeHTTP(writer, request.WithContext(context.WithValue(request.Context(), contextKey{}, device)))
		})
	}
}

func logAuthentication(logger *slog.Logger, deviceID, deviceName, remoteIP, userAgent string, success bool) {
	logger.Info("device authentication", "device_id", deviceID, "device_name", deviceName, "remote_ip", remoteIP, "user_agent", userAgent, "success", success)
}

func requestRemoteIP(request *http.Request) string {
	host, _, err := net.SplitHostPort(request.RemoteAddr)
	if err == nil {
		return host
	}
	return request.RemoteAddr
}

// DeviceFromContext returns the authenticated device stored by Middleware.
func DeviceFromContext(ctx context.Context) (devices.Device, bool) {
	device, ok := ctx.Value(contextKey{}).(devices.Device)
	return device, ok
}

func bearerToken(value string) (string, bool) {
	parts := strings.Fields(value)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") || parts[1] == "" {
		return "", false
	}
	return parts[1], true
}

func writeUnauthorized(writer http.ResponseWriter) {
	writer.Header().Set("WWW-Authenticate", "Bearer")
	httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
}

func writeInternalError(writer http.ResponseWriter) {
	httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
}
