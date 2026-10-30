# Overview

This project is intended to be a single-user, self hosted warframe.market api wrapper specifically for browsing riven mods and watchlisting certain trends to receive notifications through ntfy.sh. It also offers static resources including a riven grade calculator with OCR/manual input/warframe.market auction link, stat range lookups, and available riven attributes for different weapon categories.

Currently in early stages, no features implemented yet.

## Planned features (tentative)

There will be 2 parts to this,

1. A backend that

- updates manifests for available riven weapons, riven attributes updated based on cached warframe.market riven manifest hash.
- polls the API every minute for riven contracts, then updates riven listings and price stats for each weapon.
- supports complex queries from the database and criteria that trigger mobile push notifications

2. A frontend that

- displays current rivens, stats about their prices, with filtering and sorting options similar to the existing warframe.market auctions page. 
- a riven grader that uses OCR for screenshots of rivens and extracts text for riven grades (F to S from 0.9 to 1.1 stats from https://calamity-inc.github.io/warframe-riven-info/RivenParser.js). You can also manually enter attributes or link a warframe.market auction link.
- provides an interface to create, view, update, and delete notification rulesets on weapon, attributes, price and stats.

## Implementation

- Spring boot for the backend, Java 25 because it's the latest LTS, Maven because I like it.
- Postgres, with Redis as a shared rate limiter and caching service
- Sveltekit, never used it before but I'll be the only user so IDGAF

As a fun challenge, I'm only allowed to chat with Claude, and 'pair program' with local LLM.

## Plan

1. Setup Postgres and schemas with Liquibase. To start, we probably only need `weapons` and `attributes` with select response object fields.
2. Implement the shared Redis rate limiter. The API rate limits to 3 requests per second so we could do something like `external-api:next-allowed-at` which calculates `sleepMs = nextAllowedAt - now` and returns that after incrementing the key by lets say ~350ms. `/v1/auctions/search` needs a second, much slower limiter: it allows a burst of ~12–15, then about 1 request per 9s, and a 429 means waiting out `Retry-After` (120s). So it needs its own token bucket (e.g. burst 10, one token per 9s) alongside the general one.
3. Setup a `WarframeMarketRivenSyncService` that polls `https://api.warframe.market/v2/versions` and compares `data.collections.rivens` in the response json against a cached riven manifest key. If the key is missing or is different, call `/riven/weapon` and `/riven/attributes` then upsert. We would also need REST clients for both v1 and v2 APIs.
4. Seed the riven tables by crawling every weapon with `/v1/auctions/search`. Search caps at 100 results and is limited to ~6–7 requests a minute, so each weapon is split into filter buckets and read with paired `price_asc`/`price_desc` requests (see [Crawling a weapon's full riven book](docs/warframe-market-api.md#crawling-a-weapons-full-riven-book)). A full seed is ~2,000–2,400 requests (~5–6 h). Alongside it, poll `/v1/auctions` every minute for new listings and upsert them. We should re-crawl weapons on a rolling schedule to pick up price edits and to mark listings missing from a complete crawl as gone.

> The data is not an exact snapshot of the market, as listings deleted or edited mid-crawl can be missed, or kept around until the next crawl. That's fine, because the app only needs a rough picture of price trends per weapon and time bucket, not an exact order book.

Nothing is hard-deleted, and each price a seller sets is kept as its own row, because it represents a pricing decision made based on the market at that time:

- `riven_auctions` holds identity and lifecycle, one row per auction `id`: `weapon_slug`, `created_at`, `last_updated_at`, a reference to its metadata at `riven_snapshot_id` and a nullable `removed_at` as the soft delete. Set `removed_at` when a complete crawl of the weapon no longer returns the auction.
  > Note: there's no way to tell between auction cancellations and sales
- `riven_snapshots` holds one row per observed version of an auction, keyed `(auction_id, updated_at)` on a snapshot of `riven_auctions.updated_at`. Each row snapshots everything that can change: `buyout_price`, `starting_price`, `is_direct_sell`, `re_rolls`, `mod_rank`, `mastery_level`, `polarity` and `attributes`. When a crawl returns a new `last_updated_at`, a new row snapshot is inserted.

The last version before removal is the closest proxy for a sale price. As we're likely to miss updates between snapshots, we should prioritize rivens with the most listings. We can tune update frequency once we've crawled all weapons and build the initial database.

5. The most complex part, a `RivenStatisticsService` that calculates riven price stats by weapon including time buckets, trends etc. It should have an interface flexible enough to handle a variety of search criteria across different entities.

6. A `NotificationService` that keeps track of a set of rules defined by the user. Each rule calls `RivenStatisticsService` to determine if conditions have been met to send out a notification to a set `ntfy.sh` topic. There will also be stateful rules, which might need a persisted table or some redis magic, i.e. triggered on price differential against previous check, median weapon riven price etc etc. Everything should be cached as well to prevent lookups when nothing has changed yet for the relevant rows.
