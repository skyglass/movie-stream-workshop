package skycomposer.moviechallenge.api.movie.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MovieTypeConverter implements AttributeConverter<MovieType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(MovieType attribute) {
        return (attribute == null ? MovieType.MOVIE : attribute).getCode();
    }

    @Override
    public MovieType convertToEntityAttribute(Integer dbData) {
        return MovieType.fromCode(dbData);
    }
}
