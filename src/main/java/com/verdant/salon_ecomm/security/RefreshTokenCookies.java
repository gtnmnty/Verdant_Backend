package com.verdant.salon_ecomm.security;

import com.verdant.salon_ecomm.services.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// Builds the HTTP-only refresh-token cookie in one place. Used by AuthController (login, refresh,
// logout) and by UserRESTController, which re-issues it after a password change (M-2).
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String SAME_SITE_LAX = "Lax";

    private final RefreshTokenService refreshTokenService;

    // M-6: was app.production, which the local profile set to false and every other profile had to
    // remember to set to true. Defaults to true, so a missing setting can never send the cookie over
    // plain http. Only application-local.properties turns it off (localhost has no TLS).
    @Value("${app.cookie.secure:true}")
    private boolean secure;

    public ResponseCookie issue(String refreshToken) {
        return base(refreshToken)
            .maxAge(refreshTokenService.getRefreshTokenDurationSeconds())
            .build();
    }

    // Same name and path with max-age 0, so the browser deletes it.
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .path("/")
            .sameSite(SAME_SITE_LAX);
    }
}
