package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.dto.MovieChallengeAvailabilityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class MovieChallengeAvailabilityNotifier {

    private final MovieChallengeRepository movieChallengeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void notifyWhenChallengeAvailable(MovieRecommendedEvent event) {
        if (movieChallengeRepository.hasAvailableChallenge(event.username())) {
            messagingTemplate.convertAndSend(
                    "/topic/movie-challenges/" + event.username(),
                    new MovieChallengeAvailabilityDto(true));
        }
    }
}
