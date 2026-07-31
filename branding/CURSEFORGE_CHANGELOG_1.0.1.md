# Delvefold 1.0.1 — Stability and Correctness

- Fixed overlapping setup-screen descriptions and added landmark-density help text.
- Kept Expansive Flat terrain safely below the cloud layer.
- Fixed **Strike the Seam** so it is awarded only when a complete portal successfully ignites.
- Fixed tag-driven and multi-output ore rules so eligible outputs are selected deterministically per vein instead of later entries being shadowed.
- Added duplicate/empty-output safety diagnostics.
- Added automated NeoForge GameTests for portal frames and ore-output selection.

This patch does not change configuration schema 2 or Delvefold API version 1. Existing worlds and profiles remain compatible.
