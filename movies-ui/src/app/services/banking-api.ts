import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface BankAccount {
  accountId: string;
  customerId: string;
  availableBalance: number;
  reservedBalance: number;
  currency: string;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
}

export interface TransferResponse {
  transferId: string | null;
  state: string | null;
  message: string | null;
}

@Injectable({ providedIn: 'root' })
export class BankingApiService {
  private readonly accountBase: string;
  private readonly transferBase: string;

  constructor(private http: HttpClient, cfg: AppConfigService) {
    const c = cfg.config;
    this.accountBase = `${c.apiBaseUrl}${c.accountApiPath}`;
    this.transferBase = `${c.apiBaseUrl}${c.transferApiPath}`;
  }

  listAccounts(): Observable<BankAccount[]> {
    return this.http.get<BankAccount[]>(this.accountBase);
  }

  initiateTransfer(payload: TransferRequest): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(this.transferBase, payload);
  }

  getTransfer(transferId: string): Observable<TransferResponse> {
    return this.http.get<TransferResponse>(`${this.transferBase}/${transferId}`);
  }

  cancelTransfer(transferId: string): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(`${this.transferBase}/${transferId}/cancel`, {});
  }
}
