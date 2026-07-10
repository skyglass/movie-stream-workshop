import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService, OmdbMovieSearchResult } from '../../services/movies-api';
import { MovieSearchComponent } from './movie-search';

describe('MovieSearchComponent', () => {
  let fixture: ComponentFixture<MovieSearchComponent>;
  let component: MovieSearchComponent;
  let moviesApi: jasmine.SpyObj<Pick<MoviesApiService, 'recommendMovieFromSearch' | 'listSuggestedMovieChallenges'>> & {
    moviePageSize: number;
  };

  beforeEach(async () => {
    moviesApi = {
      moviePageSize: 12,
      recommendMovieFromSearch: jasmine.createSpy('recommendMovieFromSearch').and.returnValue(of(recommendedMovie())),
      listSuggestedMovieChallenges: jasmine.createSpy('listSuggestedMovieChallenges').and.returnValue(of({
        challenges: [],
        totalCount: 0
      }))
    };

    await TestBed.configureTestingModule({
      imports: [MovieSearchComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            token: 'token',
            isAuthenticated$: of(true),
            register: jasmine.createSpy('register')
          }
        },
        { provide: MoviesApiService, useValue: moviesApi }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovieSearchComponent);
    component = fixture.componentInstance;
  });

  it('refreshes suggested challenges after recommending a selected search result', () => {
    component.selectedMovie = selectedSearchResult();

    component.recommendSelectedMovie();

    expect(moviesApi.recommendMovieFromSearch).toHaveBeenCalled();
    expect(moviesApi.listSuggestedMovieChallenges).toHaveBeenCalledOnceWith(1, 12);
  });

  function selectedSearchResult(): OmdbMovieSearchResult {
    return {
      imdbId: 'tt0133093',
      originalTitle: 'The Matrix',
      englishTitle: 'The Matrix',
      directors: 'Lana Wachowski, Lilly Wachowski',
      writers: 'Lana Wachowski, Lilly Wachowski',
      year: '1999',
      country: 'United States',
      poster: '',
      language: 'English',
      runtime: '136 min',
      genre: 'Action, Sci-Fi',
      imdbRating: '8.7',
      type: 'MOVIE',
      typeDescription: 'Movie',
      detailsLoaded: true
    };
  }

  function recommendedMovie(): Movie {
    return {
      imdbId: 'tt0133093',
      title: 'The Matrix',
      director: 'Lana Wachowski, Lilly Wachowski',
      writer: 'Lana Wachowski, Lilly Wachowski',
      year: '1999',
      poster: '',
      genre: 'Action, Sci-Fi',
      country: 'United States',
      type: 'MOVIE',
      typeDescription: 'Movie',
      recommended: true,
      disliked: false,
      rankPosition: null,
      rating: null,
      comments: []
    };
  }
});
