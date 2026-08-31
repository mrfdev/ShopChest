# Marketplace Snapshot Source

`marketplace-snapshot.json` and `marketplace-snapshot.csv` are the reviewed
source pair for the ShopChest marketplace page on docs.1moreblock.com.

The checked-in files contain the latest reviewed marketplace snapshot. Replace
both together only with one reviewed live export created by:

```text
/shops admin export marketplace
```

See [Marketplace Snapshot Export](../marketplace-snapshot.md) for the data
boundary and publication checklist. Never hand-copy UUIDs, exact coordinates,
inventory data, or private database fields into these files.
