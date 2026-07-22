import { Pipe, PipeTransform } from '@angular/core';

// Rating/Rank are a registered viewer's own Movie Challenge standing for a movie -- never shown to anonymous
// visitors, and never shown as a bare placeholder when the viewer simply hasn't rated the movie yet (both
// fields must actually be set).
@Pipe({ name: 'showRatingRank', standalone: true })
export class ShowRatingRankPipe implements PipeTransform {
  transform(rating: number | null | undefined, rank: number | null | undefined, viewerToken: string | boolean | null | undefined): boolean {
    return !!viewerToken && rating != null && rank != null;
  }
}
