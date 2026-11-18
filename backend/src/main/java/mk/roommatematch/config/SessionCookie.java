package mk.roommatematch.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The browser keeps the login token in an HttpOnly cookie, out of reach of page scripts, so a script
 * injected into the page cannot steal it. SameSite=Strict keeps other sites from sending it along.
 */
@Component
public class SessionCookie {

    public static final String NAME = "rm_session";

    private final JwtProperties properties;

    public SessionCookie(JwtProperties properties) {
        this.properties = properties;
    }

    public void issue(String token, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, properties.expiration()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    public static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()) && !cookie.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
