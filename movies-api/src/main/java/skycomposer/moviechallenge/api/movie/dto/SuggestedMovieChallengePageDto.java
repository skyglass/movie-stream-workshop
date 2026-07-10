package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

public record SuggestedMovieChallengePageDto(List<SuggestedMovieChallengeDto> challenges, long totalCount) {
}
