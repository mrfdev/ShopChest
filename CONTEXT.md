# ShopChest

ShopChest models persistent container trading and the public storefronts through
which players discover those trades.

## Language

**Shop**:
A persistent container-backed trade for one exact product with customer-buy
and/or customer-sell terms.
_Avoid_: Store, storefront

**Customer-Buy Offer**:
The shop direction in which a customer pays the shop and receives the
configured product.
_Avoid_: Owner-sell side, buy side

**Customer-Sell Offer**:
The shop direction in which a customer supplies the configured product and
receives payment from the shop.
_Avoid_: Owner-buy side, sell-only shop

**Storefront**:
The public owner-level presentation of a player's eligible normal shops.
_Avoid_: Shop, shop container

**Storefront Profile**:
The optional owner-authored identity and descriptive information for a
storefront, independent of the authoritative shops it presents.
_Avoid_: Shop profile, store profile

**Shop Listing**:
A read-only public presentation of one shop whose product, terms, stock state,
and permitted location details come from that shop.
_Avoid_: Profile entry, owner claim

**Listing Availability**:
A point-in-time classification of whether a Shop Listing can complete one full
configured customer-buy bundle, is out of stock, is unchecked, or is
unavailable.
_Avoid_: Live guarantee, inventory status

**Featured Listing**:
One of up to three owner-selected shop listings given prominence on a
storefront and in its advertisements.
_Avoid_: Featured item, advertised text

**Tagline**:
The storefront profile's short promotional line, exposed to players through
the `advertisement` profile field (with `tagline` accepted as an alias), which
may be reused in an Advertisement.
_Avoid_: Advertisement broadcast

**Advertisement**:
A public promotion assembled from a storefront profile and eligible shop
listings.
_Avoid_: Tagline, profile

**Advertising Pass**:
A time-bounded paid allowance that permits a storefront owner to request a
limited number of advertisement broadcasts.
_Avoid_: Advertisement payment, queue entry

**Advertisement Request**:
A durable intent to use one advertising-pass allowance when a public broadcast
becomes eligible.
_Avoid_: Advertisement, cooldown

**Storefront Display**:
A persistent in-world presentation of a storefront, distinct from a temporary
advertisement broadcast.
_Avoid_: Advertisement, shop hologram

**AFK Shrine Token**:
The exact physical item currency issued through AFKShrine trades, distinct from
AFKShrine's virtual pending or claimed token balances.
_Avoid_: Light blue dye, AFK points

**Marketplace Location Scope**:
The configured boundary within which ordinary players may see exact shop
locations; it can be limited to the public marketplace or made global.
_Avoid_: Storefront visibility

**Marketplace Snapshot**:
A dated, sanitized public catalogue of the Storefronts and Shop Listings found
in the public marketplace when it was captured, rather than a live directory.
_Avoid_: Live catalogue, live stock

**Storefront Suspension**:
A staff moderation state that removes a Storefront and its Shop Listings from
public discovery without redefining or deleting its underlying shops.
_Avoid_: Hidden profile, removed shops
