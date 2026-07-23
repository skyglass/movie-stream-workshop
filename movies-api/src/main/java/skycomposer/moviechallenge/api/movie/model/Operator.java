package skycomposer.moviechallenge.api.movie.model;

import java.util.Arrays;

// The ordinal (getCode()) is persisted directly in composition_category.operator / private_composition_category.operator
// -- never reorder or remove existing values, only ever append new ones at the end.
public enum Operator {
    OR(0, "OR"),
    AND(1, "AND");

    private final int code;
    private final String label;

    Operator(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public static Operator fromCode(int code) {
        return Arrays.stream(values()).filter(operator -> operator.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown operator code: " + code));
    }
}
