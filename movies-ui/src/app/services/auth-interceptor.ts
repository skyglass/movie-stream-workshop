import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth';
import { AppConfigService } from '../config/app-config.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token;
  const config = inject(AppConfigService).config;
  const isAuthRequest = req.url.includes('/auth/token');
  const isConfigRequest = req.url.endsWith('/app-config.json') || req.url.endsWith('app-config.json');
  const isGatewayRequest = req.url.startsWith(config.apiBaseUrl);

  if (!token || isAuthRequest || isConfigRequest || !isGatewayRequest) {
    return next(req);
  }

  return next(req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  }));
};
