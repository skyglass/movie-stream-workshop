import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { BankAccount, BankingApiService, TransferResponse } from '../../services/banking-api';

@Component({
  standalone: true,
  selector: 'app-banking',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule
  ],
  templateUrl: './banking.html',
  styleUrl: './banking.css'
})
export class BankingComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly bankingApi = inject(BankingApiService);

  accounts: BankAccount[] = [];
  displayedColumns = ['accountId', 'customerId', 'availableBalance', 'reservedBalance', 'currency'];
  transferMessage = '';
  transferId = '';
  transferState: TransferResponse | null = null;
  loadingAccounts = false;
  submitting = false;

  readonly transferForm = this.fb.group({
    fromAccountId: ['ACC-201', Validators.required],
    toAccountId: ['ACC-101', Validators.required],
    amount: [100, [Validators.required, Validators.min(1)]],
    currency: ['EUR', Validators.required]
  });

  ngOnInit(): void {
    this.reloadAccounts();
  }

  reloadAccounts(): void {
    this.loadingAccounts = true;
    this.bankingApi.listAccounts().subscribe({
      next: accounts => {
        this.accounts = accounts;
        this.loadingAccounts = false;
      },
      error: err => {
        this.loadingAccounts = false;
        this.transferMessage = err?.error?.message ?? err?.message ?? 'Could not load accounts';
      }
    });
  }

  initiateTransfer(): void {
    if (this.transferForm.invalid) return;

    const value = this.transferForm.getRawValue();
    this.submitting = true;
    this.transferMessage = '';
    this.transferState = null;

    this.bankingApi.initiateTransfer({
      fromAccountId: value.fromAccountId ?? '',
      toAccountId: value.toAccountId ?? '',
      amount: Number(value.amount),
      currency: value.currency ?? 'EUR'
    }).subscribe({
      next: response => {
        this.submitting = false;
        this.transferId = response.transferId ?? '';
        this.transferState = response;
        this.transferMessage = response.transferId
          ? `Transfer accepted: ${response.transferId}`
          : response.message ?? 'Transfer response received';
      },
      error: err => {
        this.submitting = false;
        this.transferMessage = err?.error?.message ?? err?.message ?? 'Transfer failed';
      }
    });
  }

  refreshTransfer(): void {
    if (!this.transferId) return;
    this.bankingApi.getTransfer(this.transferId).subscribe({
      next: response => {
        this.transferState = response;
        this.transferMessage = `Transfer state: ${response.state ?? 'pending'}`;
        this.reloadAccounts();
      },
      error: err => {
        this.transferMessage = err?.error?.message ?? err?.message ?? 'Could not load transfer';
      }
    });
  }

  cancelTransfer(): void {
    if (!this.transferId) return;
    this.bankingApi.cancelTransfer(this.transferId).subscribe({
      next: response => {
        this.transferState = response;
        this.transferMessage = response.transferId
          ? `Cancellation accepted: ${response.transferId}`
          : response.message ?? 'Cancellation response received';
      },
      error: err => {
        this.transferMessage = err?.error?.message ?? err?.message ?? 'Could not cancel transfer';
      }
    });
  }
}
