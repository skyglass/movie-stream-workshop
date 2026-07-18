package skycomposer.moviechallenge.api.movie.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

// The ordinal (getCode()) is persisted directly in movie_guide.status -- never reorder or remove existing
// values, only ever append new ones at the end.
public enum MovieGuideStatus {
    STARTED(0, "Started"),
    COMPLETED(1, "Completed");

    private final int code;
    private final String description;

    MovieGuideStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    @JsonCreator
    public static MovieGuideStatus fromValue(String value) {
        if (value == null || value.isBlank()) return STARTED;
        String normalized = value.trim();
        if (normalized.chars().allMatch(Character::isDigit)) return fromCode(Integer.parseInt(normalized));
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(normalized)
                        || status.description.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Movie Guide status: " + value));
    }

    public static MovieGuideStatus fromCode(Integer code) {
        if (code == null) return STARTED;
        return Arrays.stream(values()).filter(status -> status.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Movie Guide status code: " + code));
    }
}
