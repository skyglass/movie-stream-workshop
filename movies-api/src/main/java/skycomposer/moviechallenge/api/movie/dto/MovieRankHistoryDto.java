package skycomposer.moviechallenge.api.movie.dto;

import java.math.BigDecimal;
import java.util.List;

public record MovieRankHistoryDto(List<RankHistoryMovieDto> higherRanks,
                                  List<RankHistoryMovieDto> lowerRanks) {

    public record RankHistoryMovieDto(String imdbId,
                                      String title,
                                      String poster,
                                      String year,
                                      String director,
                                      Integer rankPosition,
                                      BigDecimal rating) {
    }
}
