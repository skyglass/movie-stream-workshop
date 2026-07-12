import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../services/auth';
import { Movie, MoviesApiService, OmdbMovieSearchResult, SuggestedMovieChallenge } from '../../services/movies-api';
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
    expect(moviesApi.listSuggestedMovieChallenges).toHaveBeenCalledOnceWith(1, 12, false, false, false);
  });

  it('reloads suggested challenges with higher ranked first enabled', () => {
    moviesApi.listSuggestedMovieChallenges.calls.reset();

    component.toggleHigherRankedFirst({ target: { checked: true } } as unknown as Event);

    expect(component.higherRankedFirst).toBeTrue();
    expect(component.boostHigherRanks).toBeFalse();
    expect(component.moreInterestingFirst).toBeFalse();
    expect(moviesApi.listSuggestedMovieChallenges).toHaveBeenCalledOnceWith(1, 12, true, false, false);
  });

  it('enables boost higher ranks and disables higher ranked first', () => {
    component.higherRankedFirst = true;
    moviesApi.listSuggestedMovieChallenges.calls.reset();

    component.toggleBoostHigherRanks({ target: { checked: true } } as unknown as Event);

    expect(component.boostHigherRanks).toBeTrue();
    expect(component.higherRankedFirst).toBeFalse();
    expect(component.moreInterestingFirst).toBeFalse();
    expect(moviesApi.listSuggestedMovieChallenges).toHaveBeenCalledOnceWith(1, 12, false, true, false);
  });

  it('enables more interesting first and disables both rank modes', () => {
    component.higherRankedFirst = true;
    component.boostHigherRanks = true;
    moviesApi.listSuggestedMovieChallenges.calls.reset();

    component.toggleMoreInterestingFirst({ target: { checked: true } } as unknown as Event);

    expect(component.moreInterestingFirst).toBeTrue();
    expect(component.higherRankedFirst).toBeFalse();
    expect(component.boostHigherRanks).toBeFalse();
    expect(moviesApi.listSuggestedMovieChallenges).toHaveBeenCalledOnceWith(1, 12, false, false, true);
  });

  it('toggles rank help independently from probability help', () => {
    const challenge = suggestedChallenge();

    component.toggleRankHelp(challenge, challenge.movie1);

    expect(component.rankHelpVisible(challenge, challenge.movie1)).toBeTrue();
    expect(component.probabilityHelpVisible(challenge, challenge.movie1)).toBeFalse();
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

  function suggestedChallenge(): SuggestedMovieChallenge {
    return {
      movie1: {
        imdbId: 'tt101',
        title: 'Movie One',
        poster: '',
        year: '2001',
        director: 'Director One',
        winProbabilityPercent: 55,
        rankPosition: 1,
        rating: 9.46
      },
      movie2: {
        imdbId: 'tt102',
        title: 'Movie Two',
        poster: '',
        year: '2002',
        director: 'Director Two',
        winProbabilityPercent: 45,
        rankPosition: 2,
        rating: 7.25
      }
    };
  }
});
