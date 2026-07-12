import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CourseMovie, Movie, MovieCourse, MoviesApiService } from '../../services/movies-api';

@Component({standalone:true,selector:'app-movie-course-edit',imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./movie-course-edit.html',styleUrl:'./movie-course-edit.css'})
export class MovieCourseEditComponent implements OnInit {
  private api=inject(MoviesApiService);private route=inject(ActivatedRoute);private router=inject(Router);
  course?:MovieCourse;allCourses:MovieCourse[]=[];header='';title='';description='';filter='';results:Movie[]=[];loading=true;busy=false;errorMessage='';
  descriptions:Record<string,string>={};orders:Record<string,number>={};links:Record<string,number|null>={};
  ngOnInit():void{this.load();this.api.listMovieCourses().subscribe(c=>this.allCourses=c);}
  load():void{this.api.manageMovieCourse(Number(this.route.snapshot.paramMap.get('id'))).subscribe({next:c=>{this.setCourse(c);this.loading=false;},error:e=>this.fail(e)});}
  saveCourse():void{if(!this.course)return;this.busy=true;this.api.updateMovieCourse(this.course.id,this.header,this.title,this.description).subscribe({next:c=>{this.setCourse(c);this.busy=false;},error:e=>this.fail(e)});}
  search():void{if(!this.filter.trim())return;this.api.listMovies(1,20,this.filter).subscribe({next:p=>this.results=p.movies.filter(m=>!this.course?.movies.some(cm=>cm.imdbId===m.imdbId)),error:e=>this.fail(e)});}
  add(movie:Movie):void{if(!this.course)return;this.busy=true;this.api.addCourseMovie(this.course.id,{movieId:movie.imdbId,description:'',watchOrder:this.course.movies.length+1,linkedCourseId:null}).subscribe({next:c=>{this.setCourse(c);this.results=this.results.filter(m=>m.imdbId!==movie.imdbId);this.busy=false;},error:e=>this.fail(e)});}
  saveMovie(movie:CourseMovie):void{if(!this.course)return;this.busy=true;this.api.updateCourseMovie(this.course.id,movie.imdbId,{movieId:movie.imdbId,description:this.descriptions[movie.imdbId],watchOrder:this.orders[movie.imdbId],linkedCourseId:this.links[movie.imdbId]||null}).subscribe({next:c=>{this.setCourse(c);this.busy=false;},error:e=>this.fail(e)});}
  remove(movie:CourseMovie):void{if(!this.course||!confirm(`Remove ${movie.title} from this course?`))return;this.api.removeCourseMovie(this.course.id,movie.imdbId).subscribe({next:c=>this.setCourse(c),error:e=>this.fail(e)});}
  deleteCourse():void{if(!this.course||!confirm(`Delete Movie Course “${this.course.title}”?`))return;this.api.deleteMovieCourse(this.course.id).subscribe({next:()=>this.router.navigate(['/movie-courses']),error:e=>this.fail(e)});}
  private setCourse(c:MovieCourse):void{this.course=c;this.header=c.header;this.title=c.title;this.description=c.description;c.movies.forEach(m=>{this.descriptions[m.imdbId]=m.description;this.orders[m.imdbId]=m.watchOrder;this.links[m.imdbId]=m.linkedCourseId;});}
  private fail(e:any):void{this.errorMessage=e?.error?.detail??e?.error?.message??e?.message??'Could not manage Movie Course';this.loading=false;this.busy=false;}
}
