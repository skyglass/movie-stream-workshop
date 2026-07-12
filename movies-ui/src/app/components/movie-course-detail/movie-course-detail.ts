import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CourseMovie, MovieCourse, MoviesApiService } from '../../services/movies-api';

@Component({standalone:true,selector:'app-movie-course-detail',imports:[CommonModule,RouterLink],templateUrl:'./movie-course-detail.html',styleUrl:'./movie-course-detail.css'})
export class MovieCourseDetailComponent implements OnInit {
  private api=inject(MoviesApiService); private route=inject(ActivatedRoute); course?:MovieCourse; loading=true; errorMessage=''; busy:Record<string,boolean>={}; descriptionMovie?:CourseMovie;
  ngOnInit():void{this.load();} load():void{this.api.getMovieCourse(Number(this.route.snapshot.paramMap.get('id'))).subscribe({next:c=>{this.course=c;this.loading=false;},error:e=>this.fail(e)});}
  apply():void{if(!this.course)return;this.api.applyToMovieCourse(this.course.id).subscribe({next:c=>this.course=c,error:e=>this.fail(e)});}
  like(m:CourseMovie):void{this.rate(m,()=>this.api.recommendMovie(m.imdbId));} dislike(m:CourseMovie):void{this.rate(m,()=>this.api.dislikeMovie(m.imdbId));}
  clear(m:CourseMovie):void{this.rate(m,()=>this.api.unrecommendMovie(m.imdbId));}
  showDescription(m:CourseMovie):void{this.descriptionMovie=m;}
  closeDescription():void{this.descriptionMovie=undefined;}
  @HostListener('document:keydown.escape') onEscape():void{this.closeDescription();}
  poster(m:CourseMovie):string{return m.poster&&m.poster!=='N/A'?m.poster:'/images/movie-poster.jpg';}
  private rate(m:CourseMovie,request:()=>any):void{this.busy[m.imdbId]=true;request().subscribe({next:()=>{this.busy[m.imdbId]=false;this.load();},error:(e:any)=>this.fail(e)});}
  private fail(e:any):void{this.errorMessage=e?.error?.detail??e?.error?.message??e?.message??'Could not load Movie Course';this.loading=false;}
}
