package mk.roommatematch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits how often one address can try to log in and how often one student can call the language model,
 * so passwords cannot be guessed in bulk and the free model quota cannot be used up by one account.
 * Counts are kept in memory per one-minute window, which is enough for a single server.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    /** Old windows are dropped once there are this many, so the map cannot grow without end. */
    private static final int CLEANUP_AT = 10_000;

    private final RateLimitProperties limits;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties limits, ObjectMapper json, Clock clock) {
        this.limits = limits;
        this.json = json;
        this.clock = clock;
    }

    private record Window(long start, int count) {
    }

    private record Rule(String name, int limit, boolean perAddress) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        if (rule != null) {
            long now = clock.millis();
            String key = rule.name() + '|' + (rule.perAddress() ? request.getRemoteAddr() : who(request));
            Window window = windows.compute(key, (k, old) -> old == null || now - old.start() >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(old.start(), old.count() + 1));
            if (windows.size() > CLEANUP_AT) {
                windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
            }
            if (window.count() > rule.limit()) {
                reject(response, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && path.startsWith("/api/auth/")) {
            return new Rule(path, limits.auth(), true);
        }
        boolean ai = ("POST".equals(method) && (path.equals("/api/map/search") || path.equals("/api/assistant")))
                || ("GET".equals(method) && path.startsWith("/api/matches/") && path.endsWith("/explanation"));
        if (ai) {
            return new Rule("ai", limits.ai(), false);
        }
        if ("GET".equals(method) && path.equals("/api/addresses")) {
            return new Rule("addresses", limits.addresses(), false);
        }
        return null;
    }

    private static String who(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt ? "user:" + jwt.getName() : request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Премногу обиди за кратко време. Обидете се повторно за " + retryAfterSeconds + " секунди.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }
}
