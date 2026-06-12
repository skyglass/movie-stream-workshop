import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../services/auth';
import { MovieUser, MoviesApiService } from '../../services/movies-api';

@Component({
  standalone: true,
  selector: 'app-admin-users',
  imports: [CommonModule],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.css'
})
export class AdminUsersComponent implements OnInit {
  private readonly moviesApi = inject(MoviesApiService);
  readonly auth = inject(AuthService);

  users: MovieUser[] = [];
  loading = false;
  errorMessage = '';

  ngOnInit(): void {
    if (this.auth.isAdmin) {
      this.loadUsers();
    }
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMessage = '';
    this.moviesApi.listUsers().subscribe({
      next: users => {
        this.users = users;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? err?.message ?? 'Could not load users';
        this.loading = false;
      }
    });
  }

  avatar(seed: string): string {
    return `https://api.dicebear.com/9.x/avataaars/svg?seed=${encodeURIComponent(seed || 'user')}`;
  }
}
