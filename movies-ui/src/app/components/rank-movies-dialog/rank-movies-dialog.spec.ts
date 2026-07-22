import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Movie, MoviesApiService } from '../../services/movies-api';
import { RankMoviesDialogComponent } from './rank-movies-dialog';

describe('RankMoviesDialogComponent pagination', () => {
  let fixture: ComponentFixture<RankMoviesDialogComponent>;
  let component: RankMoviesDialogComponent;
  let api: jasmine.SpyObj<MoviesApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<MoviesApiService>('MoviesApiService',
      ['listPersonalityMovies', 'submitPersonalityRanking']);
    api.listPersonalityMovies.and.returnValues(
      of({ movies: movies(1, 100), totalCount: 250 }),
      of({ movies: movies(101, 100), totalCount: 250 }));

    TestBed.configureTestingModule({
      imports: [RankMoviesDialogComponent],
      providers: [{ provide: MoviesApiService, useValue: api }]
    });
    fixture = TestBed.createComponent(RankMoviesDialogComponent);
    component = fixture.componentInstance;
    component.movieGuideId = 7;
    component.guideCategoryId = 11;
    fixture.detectChanges();
  });

  it('appends the next 100 without changing any edited rank in the existing prefix', () => {
    const movedToRank100 = component.movies[0];
    component.moveToBottom(movedToRank100);
    expect(component.rankOf(movedToRank100)).toBe(100);

    component.loadNext();

    expect(component.movies.length).toBe(200);
    expect(component.rankOf(movedToRank100)).toBe(100);
    expect(component.movies[100].imdbId).toBe('tt101');
    expect(api.listPersonalityMovies.calls.argsFor(1)).toEqual([7, 2, 100, '', '', [11]]);
  });

  it('closes without submitting anything', () => {
    const closed = jasmine.createSpy('closed');
    component.closed.subscribe(closed);

    component.close();

    expect(closed).toHaveBeenCalled();
    expect(api.submitPersonalityRanking).not.toHaveBeenCalled();
  });
});

function movies(first: number, count: number): Movie[] {
  return Array.from({ length: count }, (_, index) => movie(first + index));
}

function movie(number: number): Movie {
  return {
    imdbId: `tt${number}`, title: `Movie ${number}`, director: 'Director', writer: 'Writer', year: '2000',
    poster: '', genre: 'Drama', country: 'US', type: 'MOVIE', typeDescription: 'Movie', recommended: true,
    disliked: false, rankPosition: number, rating: 1, usersRankPosition: null, usersRating: null,
    viewerRankPosition: null, viewerRating: null, comments: []
  };
}
