# EP-029 Filtered Movie Lists

## Goal

Movie list views let viewers narrow paginated results by a keyword without changing existing ordering or pagination behavior.

## Acceptance Criteria

- Movie catalog, my favorite movies, shared favorite movies, users favorite movies, and users recommended movies accept an optional `filter` request parameter.
- Empty or blank `filter` values behave the same as an omitted filter.
- Non-empty filters match movies when the keyword appears in title, director, or writer.
- Filtering is case-insensitive and preserves each list's existing sort order.
- Filtered paginated responses include `totalCount` for the matching result set.
- The UI shows a top-right `Filter` label with a keyword field and a `Clear` button while text is present.
- The UI sends filter requests only after the keyword is longer than three characters.
- `Clear` removes the keyword, reloads the unfiltered first page, and hides itself.
