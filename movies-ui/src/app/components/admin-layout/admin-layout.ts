import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  standalone: true,
  selector: 'app-admin-layout',
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayoutComponent {}
