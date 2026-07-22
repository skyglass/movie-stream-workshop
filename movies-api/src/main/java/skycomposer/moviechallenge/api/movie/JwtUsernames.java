package skycomposer.moviechallenge.api.movie;

import org.springframework.security.oauth2.jwt.Jwt;

// Resolves the username a controller should treat as "the current viewer" from an optional JWT -- null for an
// anonymous request (a null Jwt), rather than requiring every public/permitAll endpoint to duplicate this same
// claim-fallback dance to support both signed-in and anonymous callers.
public final class JwtUsernames {

    private JwtUsernames() {
    }

    public static String username(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("username"),
                jwt.getSubject());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
