---
status: accepted
---

# Capture the complete AFK Shrine Token item as the currency identity

ShopChest identifies the physical AFK Shrine Token from an administrator-captured
genuine `ItemStack`, normalized to amount one, because the live item has no
stable PDC identity and visible material, name, lore, or model data can be
copied. Currency matching therefore compares an amount-normalized candidate
with the persisted complete template using `ItemStack#isSimilar` and fails
closed when no authoritative template exists; guessed keys and partial metadata
matching are deliberately unsupported.
