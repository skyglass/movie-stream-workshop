package skycomposer.moviechallenge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MoviesApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoviesApiApplication.class, args);
    }
}
