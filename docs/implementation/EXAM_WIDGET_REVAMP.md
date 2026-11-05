# Exam widget revamp — 2.0.41

The exam editor groups setup into **Exams**, **Design** and **Details**, with a visible production-renderer preview and persistent Apply/Add widget action. Other widget editors retain their existing interface.

## Final design catalogue

The picker exposes eight distinct designs in two groups. There is no duplicate layout chooser under advanced appearance.

| Group | Design | Stored ID | Composition |
| --- | --- | --- | --- |
| Bold | Poster | POSTER | Condensed oversized type, vivid surface and cut corner |
| Bold | Orbit | ORBITAL | Preparation-window ring beside the exam |
| Bold | Aurora | AURORA | Soft mesh light and centered countdown |
| Bold | Bento | BENTO | Separate number and details tiles |
| Bold | Signal | SIGNAL | Monospace type and a technical grid |
| Glass | Frost | FROST | Smoked translucent pane and reflective rim |
| Glass | Prism | PRISM | Tinted facets and centered countdown |
| Glass | Layers | LAYERS | Offset translucent panes and asymmetric details |

New widgets use the Aurora preset through the new-widget factory. Older configurations are loaded as stored. Selecting a preset intentionally changes its visual defaults, including a starting palette for the expressive designs, while retaining exams and independent widget bindings. Fine-tuning remains available afterward.

Seven older/overlapping layouts are removed from selection: CLASSIC, HERO, TICKET, MINIMAL, TIMELINE, DASHBOARD and COLORBLOCK. Their IDs, readers and renderers remain supported for installed widgets, saved layouts and portable backups. An existing layout is kept until its owner selects a new design; editing unrelated settings or simply applying the editor preserves it. Stored-layout compatibility is deliberately separate from the eight selectable designs.

## Organization

- Exams: compact schedule rows open individual sheets. Name, date and time come first; venue, label, message, preparation dates and time zone are under More exam details. Multi-exam display/order controls appear when relevant. Reusable layout actions are collapsed.
- Design: the eight visual cards, contextual palettes and transparency/opacity controls. Custom colors, typography, spacing, corners and decorations are under Fine-tune appearance. Countdown sizing supports 24–96 sp with adaptive fitting.
- Details: countdown format, preparation timeline and main visibility controls. Additional fields, date/time formatting, end-of-exam behavior, tap actions and deadline-color thresholds are grouped in expandable controls.
- Manager: installed widgets and reusable unbound layouts are separate. Card taps open a widget/layout; secondary actions use an overflow menu. Bound configurations do not appear again in Saved layouts. Deletion checks bindings before removing a layout.

## Rendering and glass

Countdown value and unit use distinct typography, with full accessible countdown descriptions. Multi-exam rows adapt to available height and font scale. Capacity reserves actual toolbar and navigation space; normal-size wide lists can show two exams and page to the rest. Custom finished messages remain verbatim.

Orbit uses the currently displayed exam's elapsed/remaining preparation-window time, not study completion. Transparent Orbit retains its ring; a list does not display a misleading aggregate ring. Compact widths/heights and large fonts use a readable stacked arrangement for two-column designs. Layers uses Frost in those conditions. Bento keeps extra information on its details tile.

Glass uses genuine alpha transparency, tint and procedural reflective edges. RemoteViews cannot reliably blur the wallpaper behind a widget. No wallpaper capture, permissions or live-blur claim is introduced. Separate preview-only light/dark backdrops sit behind the widget; that choice is saved only as editor UI state and never enters the widget configuration or launcher bitmap.

Default glass text is checked over light backdrops. Aurora detail text remains bright across its glow. Poster and Bento choose contrasting ink on vivid surfaces; custom foreground/background/accent values remain available. All shapes and preview backdrops are procedural and bitmap-size bounded. No image/font assets, dependencies or background refresh jobs are added.

## Preservation and verification

The storage keys, independent instances, launcher binding format, full backups and scheduling model are unchanged. Drafts survive rotation, incomplete input is validated at Apply, cancellation does not write configuration, and exam removal requires confirmation. Updated-app designs/palettes require an updated app to edit or restore.

Tests cover editor restoration/cancellation, legacy layout preservation without exposing retired choices, new-widget defaults, current and historical JSON, native rendering/bounds for all 15 supported stored IDs, compact/large-font behavior, multi-exam paging, contrast/transparency, current-exam ring semantics, and productivity archive round-trip preservation. Release validation is recorded in `docs/releases/2.0.41.md`.

Screenshot galleries are production UI renders under Robolectric. Physical launcher pinning, OEM resizing and translucency behavior remain device checks. Local release builds are unsigned; production signing and publication run in GitHub Actions.
