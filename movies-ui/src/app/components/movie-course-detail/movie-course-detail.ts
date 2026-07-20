import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, of } from 'rxjs';
import { CourseMovie, MovieCourse, MoviesApiService, ShareableMovie } from '../../services/movies-api';
import { AuthService } from '../../services/auth';
import { ShareDialogComponent } from '../share-dialog/share-dialog';

@Component({standalone:true,selector:'app-movie-course-detail',imports:[CommonModule,RouterLink,ShareDialogComponent],templateUrl:'./movie-course-detail.html',styleUrl:'./movie-course-detail.css'})
export class MovieCourseDetailComponent implements OnInit {
  private api=inject(MoviesApiService); private route=inject(ActivatedRoute); readonly auth=inject(AuthService); course?:MovieCourse; loading=true; errorMessage=''; busy:Record<string,boolean>={};
  descriptionDialog:{title:string;text:string}|null=null;
  shareUrl=''; shareDialogVisible=false;
  ngOnInit():void{this.load();} load():void{this.api.getMovieCourse(Number(this.route.snapshot.paramMap.get('id'))).subscribe({next:c=>{this.course=c;this.loading=false;},error:e=>this.fail(e)});}
  apply():void{if(!this.course)return;this.api.applyToMovieCourse(this.course.id).subscribe({next:c=>this.course=c,error:e=>this.fail(e)});}
  openDescription(title:string,text:string):void{this.descriptionDialog={title,text};}
  closeDescription():void{this.descriptionDialog=null;}
  share():void{this.shareUrl=window.location.href;this.shareDialogVisible=true;}
  closeShareDialog():void{this.shareDialogVisible=false;}
  // The journey's own movie list is already fixed/ordered (watchOrder) and fully loaded with the course itself
  // -- no separate filtered fetch needed, just the same order already shown on-screen, capped to maxMovies.
  fetchOrderedMovies=(maxMovies:number):Observable<ShareableMovie[]>=>of((this.course?.movies??[]).slice(0,maxMovies));
  like(m:CourseMovie):void{this.rate(m,()=>this.api.recommendMovie(m.imdbId));} dislike(m:CourseMovie):void{this.rate(m,()=>this.api.dislikeMovie(m.imdbId));}
  clear(m:CourseMovie):void{this.rate(m,()=>this.api.unrecommendMovie(m.imdbId));}
  poster(m:CourseMovie):string{return m.poster&&m.poster!=='N/A'?m.poster:'/images/movie-poster.jpg';}
  private rate(m:CourseMovie,request:()=>any):void{this.busy[m.imdbId]=true;request().subscribe({next:()=>{this.busy[m.imdbId]=false;this.load();},error:(e:any)=>this.fail(e)});}
  private fail(e:any):void{this.errorMessage=e?.error?.detail??e?.error?.message??e?.message??'Could not load Movie Journey';this.loading=false;}
}
