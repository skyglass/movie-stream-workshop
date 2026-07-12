import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MovieCourse, MoviesApiService } from '../../services/movies-api';

@Component({ standalone: true, selector: 'app-movie-courses', imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './movie-courses.html', styleUrl: './movie-courses.css' })
export class MovieCoursesComponent implements OnInit {
  private api = inject(MoviesApiService); private router = inject(Router);
  courses: MovieCourse[] = []; loading = true; busy = false; errorMessage = '';
  creating = false; title = ''; description = '';
  ngOnInit(): void { this.load(); }
  load(): void { this.loading = true; this.api.listMovieCourses().subscribe({next: c => {this.courses=c;this.loading=false;}, error:e=>this.fail(e)}); }
  create(): void { if (!this.title.trim()) return; this.busy=true; this.api.createMovieCourse(this.title,this.description).subscribe({
    next:c=>this.router.navigate(['/movie-courses',c.id,'edit']), error:e=>this.fail(e)}); }
  apply(course: MovieCourse): void { this.busy=true; this.api.applyToMovieCourse(course.id).subscribe({
    next:()=>this.router.navigate(['/movie-courses',course.id]), error:e=>this.fail(e)}); }
  private fail(e:any):void { this.errorMessage=e?.error?.detail??e?.error?.message??e?.message??'Movie Courses request failed';this.loading=false;this.busy=false; }
}
