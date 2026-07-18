package skycomposer.moviechallenge.api.movie.model;

import java.util.Arrays;

// The ordinal (getCode()) is persisted directly in movie_guide.type -- never reorder or remove existing values,
// only ever append new ones at the end.
public enum MovieGuideType {
    GUIDE(0, "Guide"),
    PERSONALITY(1, "Personality");

    private final int code;
    private final String description;

    MovieGuideType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static MovieGuideType fromCode(int code) {
        return Arrays.stream(values()).filter(type -> type.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Movie Guide type code: " + code));
    }
}
