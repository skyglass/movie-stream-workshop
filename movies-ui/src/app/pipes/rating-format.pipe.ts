import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'ratingFormat', standalone: true })
export class RatingFormatPipe implements PipeTransform {
  transform(rating: number | null | undefined): string | null {
    return rating != null ? rating.toFixed(2) : null;
  }
}
