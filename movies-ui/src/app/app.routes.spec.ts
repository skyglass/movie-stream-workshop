import { routes } from './app.routes';

describe('public routes', () => {
  it('keeps the Movie Journeys catalog publicly accessible', () => {
    const route = routes.find(candidate => candidate.path === 'movie-journeys');

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
  });

  it('keeps Movie Journey details publicly accessible', () => {
    const route = routes.find(candidate => candidate.path === 'movie-journeys/:id');

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
  });
});
