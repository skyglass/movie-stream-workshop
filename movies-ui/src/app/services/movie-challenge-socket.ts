import { Injectable, NgZone } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

interface MovieChallengeAvailabilityMessage {
  available: boolean;
}

@Injectable({ providedIn: 'root' })
export class MovieChallengeSocketService {
  private readonly stompClient: Client;
  private subscription?: StompSubscription;
  private username?: string;
  private readonly availabilityState = new BehaviorSubject<boolean>(false);

  readonly availability$ = this.availabilityState.asObservable();

  constructor(private zone: NgZone, cfg: AppConfigService) {
    const c = cfg.config;
    const wsUrl = `${c.apiBaseUrl}${c.wsPath}`;
    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000
    });

    this.stompClient.onConnect = () => this.subscribeCurrentUser();
  }

  connect(username: string): void {
    if (!username) return;
    if (this.username === username && this.stompClient.active) return;

    this.username = username;
    this.availabilityState.next(false);

    if (this.stompClient.connected) {
      this.subscribeCurrentUser();
      return;
    }

    if (!this.stompClient.active) {
      this.stompClient.activate();
    }
  }

  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
    this.username = undefined;
    this.availabilityState.next(false);
    if (this.stompClient.active) {
      this.stompClient.deactivate();
    }
  }

  clearAvailability(): void {
    this.availabilityState.next(false);
  }

  private subscribeCurrentUser(): void {
    if (!this.username || !this.stompClient.connected) return;

    this.subscription?.unsubscribe();
    this.subscription = this.stompClient.subscribe(`/topic/movie-challenges/${this.username}`, (message: IMessage) => {
      const payload = JSON.parse(message.body) as MovieChallengeAvailabilityMessage;
      this.zone.run(() => this.availabilityState.next(payload.available));
    });
  }
}
