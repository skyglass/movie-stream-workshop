import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'rankFormat', standalone: true })
export class RankFormatPipe implements PipeTransform {
  transform(rank: number | null | undefined): string | null {
    return rank != null ? `#${rank}` : null;
  }
}
