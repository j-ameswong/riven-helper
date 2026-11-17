# Riven crawl plan

How the backend seeds and refreshes every weapon's riven listings with `/v1/auctions/search`. This
is step 4 of the [README](../README.md) plan. The API behaviour it relies on is measured in
[Crawling a weapon's full riven book](warframe-market-api.md#crawling-a-weapons-full-riven-book).

## Decisions

| Question | Decision |
| --- | --- |
| Seed and re-crawl | One loop. It always crawls the weapon crawled longest ago, never-crawled weapons first. |
| Cycle time | About 6 hours for all ~420 weapons at the search rate limit. `removed_at` has that resolution. |
| Removal confirmation | None. A listing missing from a complete crawl is marked removed. `/v1/auctions/entry/{id}` is not called, because the rate limit is the bottleneck. |
| False removals | Accepted. A listing seen again has its `removed_at` cleared. |
| Splits | Mastery rank, then polarity, then re-roll bands. No negative-stat split. |
| Bucket that still overflows | Logged and left partly read. That weapon's removals are skipped for this crawl. |
| Weapon priority | None yet: plain round robin. Tune once the first full crawl shows the real sizes. |

## Loop

A scheduled task crawls one weapon per run and starts again 5 seconds after it finishes. The search
rate limiter does the pacing, so the task spends most of its time blocked on it.

1. Pick the weapon with the oldest `weapons.last_crawled_at`, nulls first.
2. Read its book bucket by bucket (see below), archiving every page as it arrives through the same
   path the `/v1/auctions` poll uses.
3. If the whole book was read, mark the weapon's listings that weren't in it as removed.
4. Set `last_crawled_at`, whether or not the crawl succeeded.

A weapon whose crawl is cut short by a shutdown keeps the pages already archived and waits for its
next turn like any other failed crawl.

## Reading one weapon

A bucket is a set of search filters. To read one:

1. Request it sorted `price_asc`. Fewer than 95 results means that page is the whole bucket.
2. Otherwise request it sorted `price_desc`. If the lowest price on that page is strictly below the
   highest price on the `price_asc` page, the two pages are the whole bucket. A `null` buyout counts
   as infinitely expensive.
3. Otherwise split the bucket on the next filter and read each part the same way.

| Order | Filter | Values |
| --- | --- | --- |
| 1 | `mastery_rank_min`, `mastery_rank_max` | `≤8`, then `9` to `15` one each, then `16+` |
| 2 | `polarity` | `madurai`, `vazarin`, `naramon` |
| 3 | `re_rolls_min`, `re_rolls_max` | `0`, `1–3`, `4–10`, `11–25`, `26–60`, `61–150`, `151+` |

The mastery rank buckets are open-ended at both ends. Rivens in the game run from 8 to 16, but sellers
enter the level themselves, and some listings have 7.

A bucket that still overflows after all three splits holds more than ~200 listings with the same
mastery rank, polarity and re-roll band. Its two pages are kept, the rest is missed, and the crawl
counts as incomplete.

## Marking removals

After a crawl, a listing of that weapon gets `removed_at` set to the crawl's start time when all of
these hold:

- every bucket was read in full and no request failed
- the crawl returned at least one listing
- the listing has no `removed_at` yet and was not returned by the crawl
- the listing was created before the crawl started, so one the poll archived mid-crawl is left alone

Archiving a listing clears its `removed_at`. This undoes a false removal, which happens when a price
edit moves a listing across the page cutoff between the two requests of a pair.

## Failures

Any failed request ends that weapon's crawl. Pages already read stay archived, no removals are
marked, and the weapon goes to the back of the queue. A `429` also pauses the search limiter for its
`Retry-After`, so the next weapon waits it out.

## Changes

- **Schema**: `weapons.last_crawled_at` and an index on `riven_auctions(weapon_id)`. The column is
  not mapped on the `Weapon` entity, because the manifest sync saves fresh entities and would reset
  it.
- **Rate limiting**: a search request takes a slot from the search limiter and then the general one,
  and a `429` on it pauses the search limiter only, so the poll and manifest sync keep running.
- **Scheduler**: two threads instead of one, so a crawl blocked on the limiter doesn't hold up the
  poll.
- **Client**: `GET /v1/auctions/search` with the filters as query parameters.

## Left out

- The negative-stat split. Add it if the overflow warning shows up in the logs.
- Starting at the right split from the last known listing count, which skips the root pair on big
  weapons and saves about 20% of requests.
- Crawling popular weapons more often.
