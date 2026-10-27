# warframe.market API

This doc covers the public, unauthenticated warframe.market API, last checked against the live API
on **2026-10-27**. Riven endpoints come first and are covered in full, because they are the only ones
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

- One limit covers every HTTP endpoint, v1 and v2 alike: **3 requests/second**. Above that,
  Cloudflare answers `429`. Too many concurrent connections get a `509`.
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
3. To seed listings, run `/v1/auctions/search?type=riven&weapon_url_name={slug}` for each weapon.
   Search returns at most 100 results, so see [Gotchas](#gotchas).
4. After that, poll `GET /v1/auctions` for the 100 newest auctions and keep the ones where
   `item.type == "riven"`.

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

### `GET /v1/auctions/search`

Headers: `Platform`, `Language`. Returns `{ "payload": { "auctions": [Auction] } }`.

> [!IMPORTANT]
> A search returns **at most 100 auctions** and has no pagination. Every query against a popular
> weapon returned exactly 100 (checked with `kulstar`, `rubico`, `torid`, and a filter on positive
> stats only). To see more of the book for one weapon, run several searches with different `sort_by`
> and filter values.

| Param | Values | Notes |
| --- | --- | --- |
| `type` | `riven` \| `lich` \| `sister` | Required |
| `weapon_url_name` | riven weapon `slug` | Either this or `positive_stats` is required, otherwise `400 _form requirements_not_met`. An unknown slug returns `400 weapon_url_name item_not_exist`. |
| `positive_stats` | comma-separated attribute slugs | |
| `negative_stats` | comma-separated attribute slugs, or `None` | `None` means "has no negative" |
| `operation` | `allOf` \| `anyOf` | How the stat lists combine |
| `mastery_rank_min`, `mastery_rank_max` | integer | |
| `re_rolls_min`, `re_rolls_max` | integer | |
| `mod_rank` | `any` \| `maxed` | |
| `polarity` | `any` \| `madurai` \| `vazarin` \| `naramon` | Polarities observed in results |
| `buyout_policy` | `with` \| `direct` | `direct` means only fixed-price sales (`is_direct_sell: true`). `with` means auctions that have a buyout price. |
| `sort_by` | `price_desc` (default) \| `price_asc` \| `positive_attr_desc` \| `positive_attr_asc` | With the default sort and no `buyout_policy`, `buyout_price: null` auctions come first |

Lich and sister searches (`type=lich|sister`) use `element`, `ephemera`, `damage_min`, `damage_max`,
`quirk` and `buyout_policy`, and `sort_by` takes `price_*` or `damage_*`. The sister search isn't in
the v1 spec but works.

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
  to seed and refresh.
- **`/v1/auctions` ignores `type`**, so filter on `item.type == "riven"`.
- **v1 is snake_case and v2 is camelCase.** The same idea has different names in each: a v2 `slug`
  shows up in v1 as `weapon_url_name` or as an attribute's `url_name`.
- **`group` ≠ `rivenType`** on riven weapons, and `exclusiveTo` uses a mix of both vocabularies
  (`archgun`).
- **`positive` ≠ sign of `value`** on auction attributes.
- Auction ids expire. `/v1/auctions/entry/{id}` returns `404` once an auction is gone.
- An empty collection (`[]`, `data: []`, `bids: []`) is a successful response.

