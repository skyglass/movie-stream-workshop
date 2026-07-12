package skycomposer.moviechallenge.api.movie.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum MovieJourneyType {
    JOURNEY(0, "Movie Journey"),
    GUIDE(1, "Movie Guide"),
    COURSE(2, "Movie Course"),
    FESTIVAL(3, "Movie Festival"),
    TOUR(4, "Movie Tour");

    private final int code;
    private final String description;

    MovieJourneyType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    @JsonCreator
    public static MovieJourneyType fromValue(String value) {
        if (value == null || value.isBlank()) return JOURNEY;
        String normalized = value.trim();
        if (normalized.chars().allMatch(Character::isDigit)) return fromCode(Integer.parseInt(normalized));
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(normalized)
                        || type.description.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Movie Journey type: " + value));
    }

    public static MovieJourneyType fromCode(Integer code) {
        if (code == null) return JOURNEY;
        return Arrays.stream(values()).filter(type -> type.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Movie Journey type code: " + code));
    }
}
