package com.meetingmate.app.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLTransientConnectionException;

/**
 * "The database is unreachable" is an outage of a dependency, not a bug in our code,
 * so it is answered with 503 + Retry-After instead of 500. That keeps 5xx-as-bug
 * alerts meaningful and tells clients the request may succeed if retried later.
 */
public final class DatabaseUnavailable {

    public static final int RETRY_AFTER_SECONDS = 30;
    public static final String BODY =
            "{\"success\":false,\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.\",\"data\":null}";

    private DatabaseUnavailable() {
    }

    /** True if any cause in the chain means "could not get a DB connection". */
    public static boolean matches(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof CannotCreateTransactionException
                    || t instanceof DataAccessResourceFailureException
                    || t instanceof SQLTransientConnectionException) { // Hikari connection-timeout
                return true;
            }
        }
        return false;
    }

    /** For code that runs outside Spring MVC (servlet filters). */
    public static void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
