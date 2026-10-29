# warframe.market API

This doc covers the public, unauthenticated warframe.market API, last checked against the live API
on **2026-10-27** (auction search and rate limits re-checked **2026-10-29**). Riven endpoints come first and are covered in full, because they are the only ones
this project calls. Other working endpoints are listed under
[Other public endpoints](#other-public-endpoints).

## Contents

- [Basics](#basics): hosts, headers, rate limits, envelopes, errors
- [Riven endpoints](#riven-endpoints)
  - [`GET /v2/versions`](#get-v2versions)
  - [`GET /v2/riven/weapons`](#get-v2rivenweapons), [`GET /v2/riven/weapon/{slug}`](#get-v2rivenweaponslug)
  - [`GET /v2/riven/attributes`](#get-v2rivenattributes)
  - [`GET /v1/auctions`](#get-v1auctions)
  - [`GET /v1/auctions/search`](#get-v1auctionssearch)
  - [Crawling a weapon's full riven book](#crawling-a-weapons-full-riven-book)
  - [`GET /v1/auctions/entry/{id}`](#get-v1auctionsentryid), [`.../bids`](#get-v1auctionsentryidbids)
  - [Auction object](#auction-object)
- [Other public endpoints](#other-public-endpoints)
- [Dead and auth-gated endpoints](#dead-and-auth-gated-endpoints)
- [WebSocket](#websocket)
- [Gotchas](#gotchas)

---

## Basics

| | |
| --- | --- |
| v2 base | `https://api.warframe.market/v2` (current; reports `apiVersion` `0.25.0`) |
| v1 base | `https://api.warframe.market/v1` (legacy; only a few routes still work) |
| Static assets | `https://warframe.market/static/assets/` + the relative `icon` / `thumb` path from a manifest |
| WebSocket | `wss://ws.warframe.market/socket`, subprotocol `wfm` |

### Request headers

| Header | Value | Used by |
| --- | --- | --- |
| `User-Agent` | `RivenHelper/0.0.1 (+https://github.com/j-ameswong/riven-helper)` | every request (see `AGENTS.md`) |
| `Accept` | `application/json` | every request |
| `Platform` | `pc` (the only value tested) | v2 orders, all v1 |
| `Crossplay` | `true` \| `false` | v2 orders |
| `Language` | `en`, … | manifests, v1. Picks the `i18n` key that comes back. |

### Rate limits

- The general limit is **3 requests/second**. Above that, Cloudflare answers `429`. Too many
  concurrent connections get a `509`. 30 requests at 3/s to `/v2/versions` and to `/v1/auctions`
  all succeeded.
- **`/v1/auctions/search` has a much stricter limit of its own.** It acts like a leaky bucket that
  allows a burst of about 12–15 requests and refills at about 6–7 per minute. Going over it returns
  `429` with Cloudflare `error code: 1015` and `Retry-After: 120`. Some requests still get through
  during the penalty. What was observed:

  | Pace | Result |
  | --- | --- |
  | no delay | ~13 OK, then `429` |
  | 1 every 4 s | blocked after 28 |
  | 1 every 5 s | blocked after 34 |
  | 1 every 9 s | 50 of 50 OK over 7.4 min |

  Use a token bucket with a burst of about 10 and one token every 9 s, and wait out `Retry-After` on
  a `429`.
- Responses are `cf-cache-status: DYNAMIC` with no `ETag` or `Cache-Control`. Caching is up to the
  client: gate manifest refetches on [`/v2/versions`](#get-v2versions).

### Response envelopes

v1 and v2 use different envelopes and different key casing.

```jsonc
// v2: camelCase
{ "apiVersion": "0.25.0", "data": { /* or [ ... ] */ }, "error": null }

// v1: snake_case
{ "payload": { "auctions": [ /* ... */ ] } }
```

### Errors

- **v1 validation errors** return `400` with error keys keyed by field. `_form` holds errors that
  aren't about a single field:
  ```json
  {"error": {"_form": ["app.auctions.errors.search.requirements_not_met"]}}
  {"error": {"weapon_url_name": ["app.auctions.errors.item_not_exist"]}}
  ```
- **Removed v1 routes** return `404`. `/v1/items/{slug}/orders` returns `403 Deprecated` as **plain
  text**, not JSON.
- **Auth-gated routes** return `401`.
- Empty results are a `200` with `[]`, not a `404`.

---

## Riven endpoints

### Sync strategy

1. Poll `GET /v2/versions` and compare `data.collections.rivens` with the cached hash.
2. If it changed, refetch `/v2/riven/weapons` and `/v2/riven/attributes`, then upsert them.
3. To seed listings, crawl every weapon with `/v1/auctions/search`. Search returns at most 100
   results, so see [Crawling a weapon's full riven book](#crawling-a-weapons-full-riven-book).
4. After that, poll `GET /v1/auctions` for the 100 newest auctions and keep the ones where
   `item.type == "riven"`. That feed only shows new listings, so re-crawl searches periodically to
   pick up price edits, sales and deletions.

There is **no v2 auction API**. Every auction read goes through v1.

### `GET /v2/versions`

Returns hashes of the manifest collections. Each hash is a base64-encoded timestamp, but it should be
treated as an opaque value.

```json
{
  "apiVersion": "0.25.0",
  "data": {
    "id": "6addf665f8e7a2554e52ef5c",
    "apps": { "ios": "0.0.1", "android": "0.0.1", "minIos": "", "minAndroid": "" },
    "collections": {
      "items": "MjAyNi0xMC0yNVQxMjozMDoyOA==",
      "rivens": "MjAyNi0xMC0yMVQxNjowMDoxMA==",
      "liches": "…", "sisters": "…", "missions": "…", "npcs": "…", "locations": "…"
    },
    "updatedAt": "2026-10-25T12:30:28Z"
  },
  "error": null
}
```

### `GET /v2/riven/weapons`

Returns every weapon that can have a riven: 420 entries, about 160 KB. `data` is an array of:

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Mongo ObjectId |
| `slug` | string | Use this as `weapon_url_name` in auction search, e.g. `kulstar` |
| `gameRef` | string | Internal game path |
| `group` | string | Slot or category: `primary`, `secondary`, `melee`, `archgun`, `sentinel`, `zaw`, `kitgun` |
| `rivenType` | string | Which riven the weapon takes: `rifle`, `shotgun`, `pistol`, `melee`, `zaw`, `kitgun` |
| `disposition` | number | Riven disposition multiplier, observed range `0.5`–`1.55` |
| `reqMasteryRank` | integer | |
| `i18n.{lang}` | object | `{ name, icon, thumb }`. Only the requested `Language` is returned. |

`group` and `rivenType` are not the same thing. For example, archguns are `group: archgun`,
`rivenType: rifle`, and sentinel weapons map to `rifle`, `melee`, `pistol` or `shotgun`.

```json
{
  "id": "5c5ca81696e8d2003834fdcc",
  "slug": "kulstar",
  "gameRef": "/Lotus/Weapons/Grineer/Pistols/GrnTorpedoPistol/GrnTorpedoPistol",
  "group": "secondary",
  "rivenType": "pistol",
  "disposition": 1.3,
  "reqMasteryRank": 5,
  "i18n": { "en": { "name": "Kulstar", "icon": "items/images/en/kulstar.9273….png", "thumb": "items/images/en/thumbs/kulstar.9273….128x128.png" } }
}
```

### `GET /v2/riven/weapon/{slug}`

Returns one weapon with the same shape as an element of the list above, wrapped in `data`.

### `GET /v2/riven/attributes`

Returns every riven stat: 50 entries, about 15 KB. `data` is an array of:

| Field | Type | Presence | Notes |
| --- | --- | --- | --- |
| `id` | string | always | |
| `slug` | string | always | Matches `url_name` in auction attributes and the `positive_stats`/`negative_stats` search filters |
| `gameRef` | string | always | e.g. `WeaponAmmoMaxMod` |
| `group` | string | always | `default` or `melee` |
| `prefix`, `suffix` | string | always | Riven name fragments, e.g. `Ampi` / `Bin` |
| `unit` | string | 46/50 | `percent`, `multiply`, `seconds`. Missing on `punch_through`, `range`, `channeling_damage`, `parry_angle`. |
| `exclusiveTo` | string[] | 24/50 | Riven types that can roll it, e.g. `["melee","zaw"]`. Missing means any type. Includes `archgun`, which is not a `rivenType` value. |
| `positiveOnly` | `true` | 17/50 | Can't roll as a negative (all elements, `punch_through`, …) |
| `negativeOnly` | `true` | 1/50 | `chance_to_gain_combo_count` |
| `positiveIsNegative` | `true` | 1/50 | `recoil`: a positive number is the bad side |
| `i18n.{lang}` | object | always | `{ name, icon, thumb }` |

Some slugs contain `/`, e.g. `base_damage_/_melee_damage` and `fire_rate_/_attack_speed`.
URL-encode them when you put them in a query string.

### `GET /v1/auctions`

Returns the **100 most recently created** auctions of every type, newest first:
`{ "payload": { "auctions": [Auction] } }`. A `type` query parameter is **ignored**, so filter on
`item.type` yourself. In one sample, 95 of the 100 were rivens, with a few `lich` and `sister`
auctions mixed in.

The list is sorted strictly by `created`, not `updated`. An edited auction doesn't move back to the
top, and sold or deleted auctions just drop out. In one sample the 100 entries covered the last
~10.5 minutes, about 9.5 new rivens a minute, so polling once a minute doesn't miss new listings.
This endpoint takes the general 3/s limit, not the auction-search one.

### `GET /v1/auctions/search`

Headers: `Platform`, `Language`. Returns `{ "payload": { "auctions": [Auction] } }`.

> [!IMPORTANT]
> A search returns **at most 100 auctions** and has no pagination. It also has its own, much
> stricter [rate limit](#rate-limits). Every query against a popular weapon returned the cap
> (checked with `kulstar`, `rubico`, `torid`, `burston`, and a filter on positive stats only).
> `limit`, `page`, `offset`, `skip`, `price_min` and `buyout_price_min` are all silently ignored.
> To read a weapon's whole book, see
> [Crawling a weapon's full riven book](#crawling-a-weapons-full-riven-book).

> [!WARNING]
> A capped page can come back with **fewer than 100** auctions. A filtered `burston` bucket with
> 102 real listings returned 99 on both sorts. The server seems to drop hidden auctions after
> applying the cap. Treat any page of about 95 or more as truncated.

| Param | Values | Notes |
| --- | --- | --- |
| `type` | `riven` \| `lich` \| `sister` | Required |
| `weapon_url_name` | riven weapon `slug` | Either this or `positive_stats` is required, otherwise `400 _form requirements_not_met`. An unknown slug returns `400 weapon_url_name item_not_exist`. |
| `positive_stats` | comma-separated attribute slugs | Matches rivens that have the stat. One riven matches several values, so these can't split a weapon into separate groups. |
| `negative_stats` | comma-separated attribute slugs, or `none` | `none` (lowercase) means "has no negative". `None` returns `400 negative_stats app.form.invalid`. A riven has at most one negative, so `none` plus each possible negative splits a weapon's rivens into groups that don't overlap. |
| `operation` | `allOf` \| `anyOf` | How the stat lists combine |
| `mastery_rank_min`, `mastery_rank_max` | integer | Inclusive. Riven mastery levels run `8`–`16`. |
| `re_rolls_min`, `re_rolls_max` | integer | Inclusive. Sent twice, the first value wins. |
| `mod_rank` | `any` \| `maxed` | `0`, `unranked`, etc. return `400 mod_rank app.form.invalid`. There's no "unranked only" filter. |
| `polarity` | `any` \| `madurai` \| `vazarin` \| `naramon` | Polarities observed in results. Other polarities (e.g. `zenurik`) return `200` with `[]`. |
| `buyout_policy` | `with` \| `direct` | `direct` means only fixed-price sales (`is_direct_sell: true`). `with` means auctions that have a buyout price. Leave it out to include pure auctions. |
| `sort_by` | `price_desc` (default) \| `price_asc` \| `positive_attr_desc` \| `positive_attr_asc` | Sorts on `buyout_price`. With no `buyout_policy`, `buyout_price: null` auctions come **first** in `price_desc` and **last** in `price_asc`. Without `positive_stats`, `positive_attr_desc` returned the same page as `price_desc`. |

Lich and sister searches (`type=lich|sister`) use `element`, `ephemera`, `damage_min`, `damage_max`,
`quirk` and `buyout_policy`, and `sort_by` takes `price_*` or `damage_*`. The sister search isn't in
the v1 spec but works.

### Crawling a weapon's full riven book

Measured on 2026-10-29 by fully crawling `burston` and 25 randomly chosen weapons, then replaying
different split orders offline against that data.

**Pair `price_asc` with `price_desc`.** Leave `buyout_policy` out, and treat a `null` buyout as
infinitely expensive. If the lowest price on the `price_desc` page is **strictly less** than the
highest price on the `price_asc` page, the two pages together are the whole bucket. That covers up to about 200 auctions in 2 requests. If the asc page is short (under ~95),
it's already complete and the desc request isn't needed.

**Split only on filters where each riven matches exactly one value.** If the two pages don't
overlap, split the bucket and recurse:

| Filter | Values | Notes |
| --- | --- | --- |
| `mastery_rank_min` = `mastery_rank_max` | `8` … `16` | 9 groups of fairly even size. Best first split. |
| `polarity` | `madurai`, `vazarin`, `naramon` | 3 roughly equal thirds |
| `re_rolls_min` / `re_rolls_max` | bands, e.g. `0`, `1–3`, `4–10`, `11–25`, `26–60`, `61–150`, `151+` | Heavily skewed toward high roll counts |
| `negative_stats` | `none` + every non-`positiveOnly` attribute the weapon's `rivenType` can roll | ~25 values, mostly empty or tiny. Expensive, so use it last. |

`positive_stats`, `mod_rank` and `buyout_policy` don't qualify, because their values overlap.

Recommended order: **mastery rank → polarity → re-roll bands → negative**. Requests needed, out of
6,436 auctions across the 25 random weapons:

| Split order | Requests/weapon | ×420 weapons | Time at ~6.6 req/min |
| --- | --- | --- | --- |
| Best split chosen at every step (needs counts known in advance) | 4.5 | ~1,900 | 4.8 h |
| mastery rank → polarity → re-rolls → negative | 5.7 | ~2,400 | 6.0 h |
| re-rolls → polarity → mastery rank → negative | 5.5 | ~2,300 | 5.9 h |
| polarity → mastery rank → negative → re-rolls | 7.5 | ~3,100 | 7.9 h |
| negative → polarity → mastery rank → re-rolls | 13.5 | ~5,700 | 14.3 h |

What the crawl found:

- Every random weapon had at least 93 listings, with a mean of 257 and a high of 735 (`zarr`).
  `burston` alone has 2,033. Expect roughly 100k riven auctions in total.
- The book turns over fast. Of the 8,469 auctions crawled on 2026-10-29, 78% were created and 89%
  last updated in October 2026, with a long tail of stale listings back to 2019. 89% of owners were
  `offline`. No auction had `visible: false` or `closed: true`, so hidden ones aren't returned at all.
- The right split depends on the bucket's size. Up to ~200 needs no split. About 200–450 is best
  split by polarity. About 450–1,500 is best split by mastery rank. Larger still is best split by
  polarity and then mastery rank. On re-crawls, store each weapon's last count and jump straight to
  the right split, skipping the root pair.
- Every auction returned matched its filters. No filter was loosely applied.

**A crawl isn't a snapshot.** At the search rate limit, one weapon can take minutes and a full seed
takes hours, so the book changes while you read it:

- An auction deleted or sold mid-crawl is simply missing from the pages that come after it.
  Auctions deleted after their page was read stay in your data until the next crawl of that weapon.
- An auction whose price is edited between the asc and desc requests can shift across the cutoff,
  so a listing can be missed or picked up twice. Upsert on `id` to absorb duplicates.
- Auctions created mid-crawl may or may not show up. Poll `/v1/auctions` alongside the crawl to
  catch them.
- To find auctions that have gone, compare each crawl with the previous crawl of the same weapon.
  An `id` missing from a complete crawl is gone. `/v1/auctions/entry/{id}` returning `404` confirms
  it, but checking every id that way costs one request each.

### `GET /v1/auctions/entry/{id}`

Returns one auction: `{ "payload": { "auction": Auction } }`. It returns `404` once the auction is
gone.

### `GET /v1/auctions/entry/{id}/bids`

Returns `{ "payload": { "bids": [...] } }`. Most auctions have no bids, which comes back as `[]` with
a `200`.

### Auction object

These are the fields of a riven auction from `/v1/auctions` and `/v1/auctions/search`. The entry
endpoint returns the same fields.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `starting_price` | integer | Platinum |
| `buyout_price` | integer \| null | `null` means a pure auction with no buyout |
| `top_bid` | integer \| null | |
| `is_direct_sell` | boolean | `true` means a fixed-price sale (`starting_price == buyout_price`) |
| `minimal_reputation` | integer | |
| `visible`, `private`, `closed` | boolean | |
| `platform` | string | |
| `crossplay` | boolean | |
| `created`, `updated` | string | ISO-8601 with offset, e.g. `2026-10-26T23:35:48.000+00:00` |
| `note`, `note_raw` | string | Seller's note (rendered and raw) |
| `winner`, `is_marked_for`, `marked_operation_at` | null | Always `null` in every sample |
| `owner` | object | See below |
| `item` | object | See below |

**`item`** for `type: "riven"`:

| Field | Type | Notes |
| --- | --- | --- |
| `type` | string | `riven` (`lich` and `sister` items have different fields) |
| `weapon_url_name` | string | Riven weapon `slug` |
| `name` | string | Riven name, e.g. `crita-satisco` |
| `polarity` | string | `madurai` \| `vazarin` \| `naramon` |
| `mod_rank` | integer | `0`–`8` |
| `mastery_level` | integer | |
| `re_rolls` | integer | |
| `attributes` | array | `{ url_name, value, positive }` |

In `attributes`, `url_name` is an attribute `slug`. `value` is the number as the game shows it, sign
included. `positive` says whether the stat helps the weapon, not what sign the number has. For
example, `recoil` `83.5` has `positive: false` because recoil is `positiveIsNegative`, and `zoom`
`-58.3` also has `positive: false`.

**`owner`**: `id`, `slug`, `ingame_name`, `reputation`, `status` (`ingame` \| `online` \|
`offline`), `last_seen`, `avatar` (relative path or `null`), `platform`, `crossplay`, `locale`,
`region`.

---

## Other public endpoints

All of these returned `200` when last checked. This project doesn't use them.

### v2 manifests

Headers: `Language`.

| Method & path | Notes |
| --- | --- |
| `GET /v2/items` | Full tradable-item catalogue, about 3.8k items and 1.6 MB. Gate refetches on `collections.items`. |
| `GET /v2/item/{slug}` | One item |
| `GET /v2/item/{slug}/set` | The item plus every other part of its set |
| `GET /v2/lich/weapons`, `/v2/lich/weapon/{slug}` | Kuva lich weapons |
| `GET /v2/lich/ephemeras`, `/v2/lich/quirks` | |
| `GET /v2/sister/weapons`, `/v2/sister/weapon/{slug}` | Tenet sister weapons |
| `GET /v2/sister/ephemeras`, `/v2/sister/quirks` | The v1 spec misspells this as `ephermeras`. That route is dead. |
| `GET /v2/locations` | Star-chart locations |
| `GET /v2/npcs` | Plural, unlike v1's dead `/npc` |
| `GET /v2/missions` | |

### v2 orders

Headers: `Platform`, `Crossplay`, `Language`.

| Method & path | Notes |
| --- | --- |
| `GET /v2/orders/recent` | Up to 500 visible orders from online users, created in the last 4 h, newest first. The server caches it for 1 min, so polling more often than every 60 s gets nothing new. |
| `GET /v2/orders/item/{slug}` | Full visible order book (users seen within 48 h). Large. |
| `GET /v2/orders/itemId/{id}` | Same as above, keyed on item `id` |
| `GET /v2/orders/item/{slug}/top` | Up to 5 buy and 5 sell orders from online users, already ranked. Filters: `rank`, `rankLt`, `charges`, `chargesLt`, `amberStars`, `amberStarsLt`, `cyanStars`, `cyanStarsLt`, `subtype`. The `*Lt` filters win over the exact ones, and filters the item doesn't support are ignored. |
| `GET /v2/orders/itemId/{id}/top` | Same as above, keyed on item `id` |
| `GET /v2/order/{id}` | One order. Ids from `/orders/item/{slug}` resolve here, but ids from `/orders/recent` often `404`. |

### v2 users and misc

| Method & path | Notes |
| --- | --- |
| `GET /v2/user/{slug}` | Public profile |
| `GET /v2/orders/user/{slug}` | All of a user's visible orders. Uses the order headers. |
| `GET /v2/achievements/user/{slug}` | Often `data: []` |
| `GET /v2/achievements` | Every achievement definition |
| `GET /v2/dashboard/showcase` | Front-page showcase (current Prime Access, etc.) |

### v1 survivors

Headers: `Platform`, `Language`.

| Method & path | Notes |
| --- | --- |
| `GET /v1/items/{slug}/statistics` | 48 h and 90 d price-history buckets. **There is no v2 equivalent.** `include=item` also returns `include.item`. |
| `GET /v1/items/{slug}/dropsources` | Often `[]`. Takes `include=item` too. |
| `GET /v1/profile/{slug}/statistics` | Per-user trade statistics, about 1.3 MB. `/v1/profile/{slug}` itself is `404`. |

Plus the auction routes [above](#get-v1auctions).

---

## Dead and auth-gated endpoints

**Removed from v1** (most of the official v1 spec):

| v1 path | Result |
| --- | --- |
| `/items`, `/items/{slug}` | `404` |
| `/items/{slug}/orders` | `403 Deprecated` (plain text) |
| `/lich/*`, `/sister/*`, `/riven/*` | `404`. Moved to v2. |
| `/locations`, `/npc`, `/missions` | `404`. Moved to v2. |
| `/profile/{user}` | `404` |

**Auth-gated** routes are live and return `401` without credentials. This project doesn't use them:

- `POST /v2/auth/signin`, `/auth/signup`, `/auth/refresh`, `/auth/signout`
- `GET|PATCH /v2/me`, `POST /v2/me/avatar`, `POST /v2/me/background`
- `GET /v2/orders/my`
- `POST /v2/order`, `PATCH|DELETE /v2/order/{id}`, `POST /v2/order/{id}/close`
- `PATCH /v2/orders/group/{id}`
- v1 `/profile/orders`, `/settings/notifications/push`, `/auctions/create`, `/im/chats`

---

## WebSocket

`wss://ws.warframe.market/socket` with subprotocol `wfm`. Messages are JSON envelopes with a `route`,
an optional `id` and a `payload`:

```json
{"route":"@wfm|cmd/subscribe/newOrders","id":"t1-capture","payload":{"platform":"pc","crossplay":true}}
```

After subscribing, the server sends `subscribe/newOrders:ok`, `reports/online` and
`@wfm|event/subscriptions/newOrder` frames. The `newOrder` payload is a v2 order. Only `newOrders`
has been tried, so nobody has checked for an auction feed, and rivens still need the REST polling
described above.

---

## Gotchas

- **Auction search stops at 100 results** and has no pagination or sort-by-newest. You can't read a
  weapon's whole riven book in one call. Use `/v1/auctions` for new listings and per-weapon searches
  to seed and refresh. See [Crawling a weapon's full riven book](#crawling-a-weapons-full-riven-book).
- **Auction search is rate-limited to about 6–7 requests a minute**, not 3/s. A `429` there carries
  `Retry-After: 120`.
- **A capped search page can hold 99 auctions** instead of 100. Don't treat "fewer than 100" as
  "complete".
- **`/v1/auctions` is ordered by `created`.** Price edits, sales and deletions never show up there.
- **`/v1/auctions` ignores `type`**, so filter on `item.type == "riven"`.
- **v1 is snake_case and v2 is camelCase.** The same idea has different names in each: a v2 `slug`
  shows up in v1 as `weapon_url_name` or as an attribute's `url_name`.
- **`group` ≠ `rivenType`** on riven weapons, and `exclusiveTo` uses a mix of both vocabularies
  (`archgun`).
- **`positive` ≠ sign of `value`** on auction attributes.
- Auction ids expire. `/v1/auctions/entry/{id}` returns `404` once an auction is gone.
- An empty collection (`[]`, `data: []`, `bids: []`) is a successful response.

