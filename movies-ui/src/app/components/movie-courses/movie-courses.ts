import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Movie, MovieCategory, MovieCourse, MovieJourneyType, MoviesApiService, OmdbMovieSearchResult, ParsedMovieSearch } from '../../services/movies-api';
import { AuthService } from '../../services/auth';
import { CategoryTreeDialogComponent } from '../category-tree-dialog/category-tree-dialog';
import { ExternalMovieAction, MovieFilterSearchComponent } from '../movie-filter-search/movie-filter-search';

@Component({ standalone: true, selector: 'app-movie-courses', imports: [CommonModule, FormsModule, RouterLink, CategoryTreeDialogComponent, MovieFilterSearchComponent],
  templateUrl: './movie-courses.html', styleUrls: ['./movie-courses.css', './movie-courses-category.css'] })
export class MovieCoursesComponent implements OnInit {
  private api = inject(MoviesApiService); private router = inject(Router); private route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  courses: MovieCourse[] = []; loading = true; busy = false; errorMessage = '';
  readonly journeyTypes:{value:MovieJourneyType;label:string}[]=[{value:'JOURNEY',label:'Movie Journey'},{value:'GUIDE',label:'Movie Guide'},{value:'COURSE',label:'Movie Course'},{value:'FESTIVAL',label:'Movie Festival'},{value:'TOUR',label:'Movie Tour'}];
  creating = false; header = ''; title = ''; description = ''; type:MovieJourneyType='JOURNEY';
  startJourneyOpen=false;journeyCategories:MovieCategory[]=[];categoriesTree:MovieCategory[]=[];journeyMovies:Movie[]=[];journeyLoading=false;recommendationBusy:Record<string,boolean>={};
  addMovieOpen=false;addMovieFilter='';addMovieResults:Movie[]=[];addMovieBusy:Record<string,boolean>={};
  descriptionCourse:MovieCourse|null=null;
  @ViewChild(MovieFilterSearchComponent) addMovieFilterSearch?:MovieFilterSearchComponent;

  ngOnInit(): void {
    this.load();
    const raw = this.route.snapshot.queryParamMap.get('category');
    const categoryIds = raw ? raw.split(',').map(Number).filter(id => !!id) : [];
    if (categoryIds.length) {
      this.startJourneyOpen = true;
      this.restoreJourneyCategories(categoryIds);
    }
  }

  load(): void { this.loading = true; this.api.listMovieCourses().subscribe({next: c => {this.courses=c;this.loading=false;}, error:e=>this.fail(e)}); }
  create(): void { if (!this.header.trim() || !this.title.trim()) return; this.busy=true; this.api.createMovieCourse(this.header,this.title,this.description,this.type).subscribe({
    next:c=>this.router.navigate(['/movie-journeys',c.id,'edit']), error:e=>this.fail(e)}); }
  apply(course: MovieCourse): void { this.busy=true; this.api.applyToMovieCourse(course.id).subscribe({
    next:()=>this.router.navigate(['/movie-journeys',course.id]), error:e=>this.fail(e)}); }

  get journeyCategoryIds(): number[] { return this.journeyCategories.map(c => c.id); }

  startJourney(): void { this.startJourneyOpen = true; }

  closeStartJourney(): void {
    this.startJourneyOpen = false;
    this.journeyCategories = [];
    this.journeyMovies = [];
    this.addMovieOpen = false;
    this.addMovieResults = [];
    this.addMovieFilter = '';
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  onJourneyCategoriesSelected(ids: number[]): void {
    if (this.categoriesTree.length) { this.applyJourneyCategorySelection(ids); return; }
    this.api.getCategoryTree().subscribe({
      next: categories => { this.categoriesTree = categories; this.applyJourneyCategorySelection(ids); },
      error: e => this.fail(e)
    });
  }

  toggleAddMovie(): void { this.addMovieOpen = !this.addMovieOpen; }

  searchMoviesToAdd(parsed: ParsedMovieSearch = this.api.parseMovieSearch(this.addMovieFilter)): void {
    if (!parsed.keyword) { this.addMovieResults = []; return; }
    this.api.listMovies(1, 20, parsed.keyword, parsed.year).subscribe({
      next: page => this.addMovieResults = page.movies.filter(movie => !this.journeyMovies.some(added => added.imdbId === movie.imdbId)),
      error: e => this.fail(e)
    });
  }

  addMovieToCategory(movie: Movie): void {
    if (this.journeyCategories.length === 0 || this.addMovieBusy[movie.imdbId]) return;
    this.addMovieBusy[movie.imdbId] = true;
    this.api.saveMovieCategories(movie.imdbId, this.journeyCategories.map(c => c.id), []).subscribe({
      next: () => {
        this.addMovieBusy[movie.imdbId] = false;
        this.closeAddMoviePanel();
      },
      error: e => { this.addMovieBusy[movie.imdbId] = false; this.fail(e); }
    });
  }

  externalAddToCategory(event: { action: ExternalMovieAction; movie: OmdbMovieSearchResult }): void {
    if (this.journeyCategories.length === 0) return;
    forkJoin(this.journeyCategories.map(c => this.api.addMovieFromSearchToCategory(c.id, event.movie))).subscribe({
      next: () => {
        this.addMovieFilterSearch?.completeExternalAction();
        this.closeAddMoviePanel();
      },
      error: e => { this.addMovieFilterSearch?.failExternalAction(e); this.fail(e); }
    });
  }

  openDescription(course:MovieCourse):void{this.descriptionCourse=course;}
  closeDescription():void{this.descriptionCourse=null;}

  poster(movie:Movie):string{return movie.poster&&movie.poster!=='N/A'?movie.poster:'/images/movie-poster.jpg';}
  rate(movie:Movie,positive:boolean):void{if(this.recommendationBusy[movie.imdbId])return;this.recommendationBusy[movie.imdbId]=true;const request=positive?this.api.recommendMovie(movie.imdbId):this.api.dislikeMovie(movie.imdbId);request.subscribe({next:updated=>{movie.recommended=updated.recommended;movie.disliked=updated.disliked;this.recommendationBusy[movie.imdbId]=false;},error:e=>this.fail(e)});}
  clearRating(movie:Movie):void{if(this.recommendationBusy[movie.imdbId])return;this.recommendationBusy[movie.imdbId]=true;this.api.unrecommendMovie(movie.imdbId).subscribe({next:updated=>{movie.recommended=updated.recommended;movie.disliked=updated.disliked;this.recommendationBusy[movie.imdbId]=false;},error:e=>this.fail(e)});}

  private applyJourneyCategorySelection(ids: number[]): void {
    this.journeyCategories = ids.map(id => this.findCategory(this.categoriesTree, id)).filter((c): c is MovieCategory => !!c);
    this.addMovieOpen = false;
    this.addMovieResults = [];
    this.addMovieFilter = '';
    this.reloadJourneyMovies();
    this.router.navigate([], { relativeTo: this.route, queryParams: { category: ids.join(',') || null }, replaceUrl: true });
  }

  private closeAddMoviePanel(): void {
    this.addMovieOpen = false;
    this.addMovieResults = [];
    this.addMovieFilter = '';
    this.reloadJourneyMovies();
  }

  private restoreJourneyCategories(categoryIds: number[]): void {
    this.journeyLoading = true;
    this.api.getCategoryTree().subscribe({
      next: categories => {
        this.categoriesTree = categories;
        this.journeyCategories = categoryIds.map(id => this.findCategory(categories, id)).filter((c): c is MovieCategory => !!c);
        if (this.journeyCategories.length) this.reloadJourneyMovies(); else this.journeyLoading = false;
      },
      error: e => this.fail(e)
    });
  }

  private findCategory(categories: MovieCategory[], id: number): MovieCategory | undefined {
    for (const category of categories) {
      if (category.id === id) return category;
      const found = this.findCategory(category.children, id);
      if (found) return found;
    }
    return undefined;
  }

  private reloadJourneyMovies(): void {
    if (this.journeyCategories.length === 0) { this.journeyMovies = []; return; }
    this.journeyLoading = true;
    this.api.listMovies(1, 50, '', '', this.journeyCategories.map(c => c.id)).subscribe({
      next: page => { this.journeyMovies = page.movies; this.journeyLoading = false; },
      error: e => this.fail(e)
    });
  }

  private fail(e:any):void { this.errorMessage=e?.error?.detail??e?.error?.message??e?.message??'Movie Journeys request failed';this.loading=false;this.busy=false; }
}
