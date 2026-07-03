package skycomposer.moviechallenge.api.movie.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum MovieType {
    MOVIE(0, "movie", "Movie"),
    SERIES(1, "series", "Series"),
    EPISODE(2, "episode", "Episode");

    private final int code;
    private final String omdbValue;
    private final String description;

    MovieType(int code, String omdbValue, String description) {
        this.code = code;
        this.omdbValue = omdbValue;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getOmdbValue() {
        return omdbValue;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static MovieType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return MOVIE;
        }

        String normalized = value.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            return fromCode(Integer.parseInt(normalized));
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(normalized)
                        || type.omdbValue.equalsIgnoreCase(normalized)
                        || type.description.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown movie type: " + value));
    }

    public static MovieType fromCode(Integer code) {
        if (code == null) {
            return MOVIE;
        }

        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown movie type code: " + code));
    }
}
