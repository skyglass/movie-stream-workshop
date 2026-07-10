import { Injectable } from '@angular/core';
import type { AppConfig } from './app-config.model';

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private readonly requiredStringKeys: Array<keyof AppConfig> = [
    'apiBaseUrl',
    'authTokenPath',
    'clientId',
    'keycloakBaseUrl',
    'keycloakRealm',
    'uiBaseUrl',
    'moviesApiPath',
    'movieChallengesPath',
    'favoriteMoviesPath',
    'publicFavoriteMoviesPath',
    'usersFavoriteMoviesPath',
    'usersRecommendedMoviesPath',
    'userExtrasPath',
    'usersApiPath',
    'omdbBaseUrl',
    'wsPath'
  ];

  private cfg!: AppConfig;

  async load(): Promise<void> {
    const res = await fetch('app-config.json', { cache: 'no-store' });
    if (!res.ok) throw new Error('Cannot load app-config.json');
    const config = await res.json() as Partial<AppConfig>;
    this.validate(config);
    this.cfg = config;
  }

  get config(): AppConfig {
    if (!this.cfg) throw new Error('AppConfig not loaded yet');
    return this.cfg;
  }

  private validate(config: Partial<AppConfig>): asserts config is AppConfig {
    const missingKeys = this.requiredStringKeys.filter(key => {
      const value = config[key];
      return typeof value !== 'string' || value.trim() === '';
    });

    if (missingKeys.length > 0) {
      throw new Error(`app-config.json is missing required config values: ${missingKeys.join(', ')}`);
    }
  }
}
