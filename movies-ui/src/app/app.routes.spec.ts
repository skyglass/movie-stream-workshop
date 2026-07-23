import { routes } from './app.routes';

describe('public routes', () => {
  it('keeps the My Watchlists landing page publicly accessible', () => {
    const route = routes.find(candidate => candidate.path === 'my-watchlists');

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
  });
});
