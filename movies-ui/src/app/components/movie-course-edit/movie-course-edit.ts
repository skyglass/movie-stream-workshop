import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CourseMovie, Movie, MovieCourse, MovieJourneyType, MoviesApiService, ParsedMovieSearch } from '../../services/movies-api';
import { MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';

@Component({standalone:true,selector:'app-movie-course-edit',imports:[CommonModule,FormsModule,RouterLink,MovieFilterSearchComponent],templateUrl:'./movie-course-edit.html',styleUrl:'./movie-course-edit.css'})
export class MovieCourseEditComponent implements OnInit {
  private api=inject(MoviesApiService);private route=inject(ActivatedRoute);private router=inject(Router);
  readonly journeyTypes:{value:MovieJourneyType;label:string}[]=[{value:'JOURNEY',label:'Movie Journey'},{value:'GUIDE',label:'Movie Guide'},{value:'COURSE',label:'Movie Course'},{value:'FESTIVAL',label:'Movie Festival'},{value:'TOUR',label:'Movie Tour'}];
  course?:MovieCourse;header='';title='';description='';type:MovieJourneyType='JOURNEY';filter='';results:Movie[]=[];loading=true;busy=false;errorMessage='';addingMovie=false;
  headers:Record<string,string>={};descriptions:Record<string,string>={};
  editingMovies:Record<string,boolean>={};
  ngOnInit():void{this.load();}
  load():void{this.api.manageMovieCourse(Number(this.route.snapshot.paramMap.get('id'))).subscribe({next:c=>{this.setCourse(c);this.loading=false;},error:e=>this.fail(e)});}
  saveCourse():void{if(!this.course)return;this.busy=true;this.api.updateMovieCourse(this.course.id,this.header,this.title,this.description,this.type).subscribe({next:c=>this.router.navigate(['/movie-journeys',c.id]),error:e=>this.fail(e)});}
  search(parsed:ParsedMovieSearch=this.api.parseMovieSearch(this.filter)):void{if(!parsed.keyword){this.results=[];return;}this.api.listMovies(1,20,parsed.keyword,parsed.year).subscribe({next:p=>{this.results=p.movies.filter(m=>!this.course?.movies.some(cm=>cm.imdbId===m.imdbId));},error:e=>this.fail(e)});}
  toggleAddMovie():void{this.addingMovie=!this.addingMovie;}
  poster(movie:CourseMovie):string{return movie.poster&&movie.poster!=='N/A'?movie.poster:'/images/movie-poster.jpg';}
  add(movie:Movie):void{if(!this.course)return;this.busy=true;this.api.addCourseMovie(this.course.id,{movieId:movie.imdbId,header:'',description:'',watchOrder:this.course.movies.length+1,linkedCourseId:null}).subscribe({next:c=>{this.setCourse(c);this.results=this.results.filter(m=>m.imdbId!==movie.imdbId);this.busy=false;},error:e=>this.fail(e)});}
  editMovie(movie:CourseMovie):void{this.headers[movie.imdbId]=movie.header;this.descriptions[movie.imdbId]=movie.description;this.editingMovies[movie.imdbId]=true;}
  saveMovie(movie:CourseMovie):void{if(!this.course)return;this.busy=true;this.api.updateCourseMovie(this.course.id,movie.imdbId,{movieId:movie.imdbId,header:this.headers[movie.imdbId],description:this.descriptions[movie.imdbId],watchOrder:movie.watchOrder,linkedCourseId:movie.linkedCourseId}).subscribe({next:c=>{this.setCourse(c);this.editingMovies[movie.imdbId]=false;this.busy=false;},error:e=>this.fail(e)});}
  remove(movie:CourseMovie):void{if(!this.course||!confirm(`Remove ${movie.title} from this journey?`))return;this.api.removeCourseMovie(this.course.id,movie.imdbId).subscribe({next:c=>this.setCourse(c),error:e=>this.fail(e)});}
  deleteCourse():void{if(!this.course||!confirm(`Delete Movie Journey “${this.course.title}”?`))return;this.api.deleteMovieCourse(this.course.id).subscribe({next:()=>this.router.navigate(['/movie-journeys']),error:e=>this.fail(e)});}
  private setCourse(c:MovieCourse):void{this.course=c;this.header=c.header;this.title=c.title;this.description=c.description;this.type=c.type;c.movies.forEach(m=>{this.headers[m.imdbId]=m.header;this.descriptions[m.imdbId]=m.description;});}
  private fail(e:any):void{this.errorMessage=e?.error?.detail??e?.error?.message??e?.message??'Could not manage Movie Journey';this.loading=false;this.busy=false;}
}
