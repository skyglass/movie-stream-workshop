package skycomposer.moviechallenge.api.movie;

import java.util.Locale;

// Shared slug rule for a Movie Personality's synthetic "ranked as this personality" username (see
// MovieGuideService.submitRanking / CategoryService.syncGuideNameIfAnchor) -- kept as a tiny dependency-free
// utility rather than a Spring bean so both services can call it without a circular bean dependency between them.
final class PersonaUsernames {
    private PersonaUsernames() {
    }

    static String slugify(String name) {
        return name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
