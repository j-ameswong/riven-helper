# Overview

This project is intended to be a single-user, self hosted warframe.market api wrapper specifically for browsing riven mods and watchlisting certain trends to receive notifications through ntfy.sh. It also offers static resources including a riven grade calculator with OCR/manual input/warframe.market auction link, stat range lookups, and available riven attributes for different weapon categories.

There's still no code, currently still busy planning.

## Planned features (tentative)

There will be 2 parts to this, 

1. A backend that 
- updates manifests for available riven weapons, riven attributes updated based on cached warframe.market riven manifest hash.
- polls the API every minute for riven contracts, then updates riven listings and price stats for each weapon.

2. A frontend that
- displays current rivens, stats about their prices, with filtering and sorting options similar to the existing warframe.market auctions page. (note: there is no sorting by new, so we have to fetch all rivens per weapon and process the json)
- a riven grader that uses OCR for screenshots of rivens and extracts text for riven grades (F to S from 0.9 to 1.1 stats from https://calamity-inc.github.io/warframe-riven-info/RivenParser.js). You can also manually enter attributes or link a warframe.market auction link.
- provides an interface to create, view, update, and delete notification rulesets on weapon, attributes, price and stats.

## Implementation

- Spring boot for the backend, Java 25 because it's the latest LTS, Maven because I like it.
- Postgres, with Redis as a shared rate limiter and caching service
- Sveltekit, never used it before but I'll be the only user so IDGAF

As a fun challenge, I'm only allowed to chat with Claude, and 'pair program' with local LLM.

## Plan
1. Setup Postgres and schemas with Liquibase. To start, we probably only need `weapons` and `attributes` with select response object fields.
2. Implement the shared Redis rate limiter. The API rate limits to 3 requests per second so we could do something like `external-api:next-allowed-at` which calculates `sleepMs = nextAllowedAt - now` and returns that after incrementing the key by lets say ~350ms.
3. Setup a `WarframeMarketRivenSyncService` that polls `https://api.warframe.market/v2/versions` and compares `data.collections.rivens` in the response json against a cached riven manifest key. If the key is missing or is different, call `/riven/weapon` and `/riven/attributes` then upsert. We would also need REST clients for both v1 and v2 APIs.
4. On app start, for each weapon, fetch `https://api.warframe.market/v1/auctions/search?type=riven&weapon_url_name={Weapon.slug}&buyout_policy=direct'`, then upsert to a `rivens` table. Otherwise, poll `/auctions` for the 100 most recent orders and upsert those as appropriate.
5. The most complex part, a `RivenStatisticsService` that calculates riven price stats by weapon including time buckets, trends etc. It should have an interface flexible enough to handle a variety of search criteria across different entities.
6. A `NotificationService` that keeps track of a set of rules defined by the user. Each rule calls `RivenStatisticsService` to determine if conditions have been met to send out a notification to a set `ntfy.sh` topic. There will also be stateful rules, which might need a persisted table or some redis magic, i.e. triggered on price differential against previous check, median weapon riven price etc etc. Everything should be cached as well to prevent lookups when nothing has changed yet for the relevant rows.
