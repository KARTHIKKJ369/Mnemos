# Mnemos — MASTER.md
> Design system, architecture, and API integration reference

---

## Visual Thesis: "Obsidian Archive"

Near-black slate surfaces. Pure zinc neutrals. Zero gradients, zero glass.
The UI should feel like a premium archival tool — calm, fast, trustworthy.
Every element earns its place. Every pixel is intentional.

---

## Interaction Thesis: Apple-Physics

- **Pointer-down feedback**: instant — scale(0.97) on `active:`, 80ms
- **Enter animations**: ease-out, 220ms spring (bounce: 0, duration: 0.22)
- **Exit animations**: slightly faster — 150ms
- **No bounce** unless gesture carried momentum
- **No layout animations** — only `transform` and `opacity`
- **Scale range**: 0.96–1.0 only (never 0)
- Springs via `motion/react` (Motion library)

---

## Color Palette

| Token | Value | Use |
|---|---|---|
| `--color-surface-base` | `#09090b` | Page background |
| `--color-surface-raised` | `#111113` | Elevated panels |
| `--color-surface-overlay` | `#18181b` | Cards, sidebars |
| `--color-surface-subtle` | `#27272a` | Inputs, chips |
| `--color-surface-muted` | `#3f3f46` | Scrollbars, dividers |
| `--color-border-default` | `#27272a` | Default borders |
| `--color-border-subtle` | `#18181b` | Hairline separators |
| `--color-border-bright` | `#3f3f46` | Hover borders |
| `--color-text-primary` | `#fafafa` | Headings, values |
| `--color-text-secondary` | `#a1a1aa` | Labels, descriptions |
| `--color-text-muted` | `#71717a` | Placeholders, icons |
| `--color-text-disabled` | `#52525b` | Disabled states |
| `--color-accent` | `#e4e4e7` | CTAs, focus rings |
| `--color-accent-dim` | `#a1a1aa` | Accent hover |
| `--color-danger` | `#ef4444` | Errors, destructive |
| `--color-success` | `#22c55e` | Confirmations |
| `--color-warning` | `#f59e0b` | Caution states |

---

## Typography

- **Font**: `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
- **Mono**: `ui-monospace, "JetBrains Mono", "SF Mono", Menlo, monospace`
- **Optical sizing**: `font-optical-sizing: auto`
- **Anti-aliasing**: `-webkit-font-smoothing: antialiased`
- **Tracking**: Tight on large headings (`-0.02em`), neutral on body
- **Scale**: 10px (labels) → 11px (meta) → 12px (small) → 13px (body) → 14px (default) → 16px (sm headings) → 20px (h2)

---

## Spacing (4px base)

`4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64 · 80 · 96px`

---

## Radii

| Token | Value |
|---|---|
| `--radius-sm` | 4px |
| `--radius-md` | 8px |
| `--radius-lg` | 12px |
| `--radius-xl` | 16px |
| `--radius-2xl` | 24px |
| `--radius-full` | 9999px |

---

## Motion Tokens

| Token | Value |
|---|---|
| `--duration-instant` | 80ms |
| `--duration-fast` | 150ms |
| `--duration-normal` | 220ms |
| `--duration-slow` | 350ms |
| Spring default | `{ type: 'spring', bounce: 0, duration: 0.22 }` |
| Spring momentum | `{ type: 'spring', bounce: 0.2, duration: 0.3 }` |

---

## Architecture

```
src/
├── api/
│   ├── client.ts          — Typed API client (all 17 endpoints)
│   └── clientHelpers.ts   — Base URL helper
├── hooks/
│   └── useMedia.ts        — TanStack Query hooks for media
├── stores/
│   ├── auth.ts            — Zustand auth session (persisted)
│   ├── upload.ts          — Upload queue state
│   └── ui.ts              — Viewer, selection, toast state
├── types/
│   └── index.ts           — All types mirrored from backend models
├── features/
│   ├── auth/              — Device registration page
│   ├── gallery/           — Virtualized photo grid + timeline
│   ├── viewer/            — Fullscreen media viewer
│   ├── upload/            — Upload queue panel + engine
│   ├── search/            — Instant search with filters
│   ├── favorites/         — Favorited media grid
│   ├── trash/             — Soft-delete placeholder
│   ├── sync/              — Sync diff status + ack controls
│   ├── devices/           — Device registration + server health
│   ├── vaults/            — Vault creation (legacy + encrypted)
│   └── settings/          — Session management + server config
├── layouts/
│   └── AppLayout.tsx      — Sidebar navigation shell
├── components/
│   ├── ui/
│   │   ├── Button.tsx     — 5 variants, 4 sizes, Apple press
│   │   ├── Input.tsx      — With optional left icon
│   │   └── Toasts.tsx     — Animated toast stack
│   └── shared/
│       └── AuthImage.tsx  — Auth-header image with object URL lifecycle
├── routes/
│   ├── __root.tsx         — Auth guard
│   ├── _app.tsx           — Layout route
│   └── _app/              — All page routes
├── lib/
│   └── utils.ts           — cn, formatBytes, groupByMonth, hashFile, etc.
└── styles/
    └── globals.css        — Design tokens + base reset
```

---

## API Integration

### Fully implemented endpoints

| Method | Path | Frontend hook/fn |
|---|---|---|
| `POST` | `/devices/register` | `registerDevice()` |
| `GET` | `/health` | `getHealth()` (useQuery) |
| `GET` | `/files/exists` | `checkFileExists()` |
| `POST` | `/upload` | `uploadFile()` (XHR + progress) |
| `GET` | `/sync/diff` | `getSyncDiff()` (useQuery) |
| `POST` | `/sync/ack` | `ackSync()` (useMutation) |
| `GET` | `/media` | `searchMedia()` → `useMediaSearch`, `useMediaInfinite` |
| `GET` | `/media/{id}` | `getMedia()` → `useMediaDetail` |
| `GET` | `/media/{id}/original` | `fetchMediaBlob(id, 'original')` |
| `GET` | `/media/{id}/thumbnail` | `fetchMediaBlob(id, 'thumbnail')` |
| `GET` | `/media/{id}/preview` | `fetchMediaBlob(id, 'preview')` |
| `POST` | `/media/{id}/favorite` | `favoriteMedia()` → `useFavoriteMedia` |
| `DELETE` | `/media/{id}/favorite` | `unfavoriteMedia()` → `useFavoriteMedia` |
| `DELETE` | `/media/{id}` | `deleteMedia()` → `useDeleteMedia` |
| `POST` | `/vaults` | `createVault()` |

### Known backend gaps (clearly isolated in UI)

| Gap | Location | Notes |
|---|---|---|
| `GET /devices` — list all devices | `DevicesPage.tsx` | Backend only has register |
| `GET /media?deleted=true` — trash view | `TrashPage.tsx` | Soft-delete exists, no list |
| Restore from trash | Not implemented | No undelete endpoint |
| Vault listing | `VaultsPage.tsx` | Only POST /vaults implemented |
| Vault file management | `VaultsPage.tsx` | E2EE upload handler stubbed |
| Locked folder endpoints | Not implemented | ROADMAP Phase 8 |

---

## Routing

```
/ (root — auth guard)
└── /_app (AppLayout sidebar)
    ├── /gallery      — Virtualized photo grid (default)
    ├── /timeline     — Month-grouped timeline
    ├── /search       — Full-text + filtered search
    ├── /favorites    — Favorited media
    ├── /trash        — Soft-deleted (pending backend)
    ├── /sync         — Sync diff status + ack
    ├── /devices      — Device registration + health
    ├── /vaults       — Vault create + parameters
    └── /settings     — Session + server config
```

---

## State Management

```
TanStack Query (server state)
├── mediaKeys.list(params)      — Gallery/search pages
├── mediaKeys.infinite(params)  — Infinite scroll
├── mediaKeys.detail(id)        — Viewer metadata
├── mediaKeys.blob(id, type)    — Object URL cache (∞ stale)
└── ['sync-diff', since]        — Sync status

Zustand (client state)
├── useAuthStore    — session, deviceId, token (localStorage)
├── useUploadStore  — queue, progress, status per file
└── useUIStore      — viewerMediaId, selectedIds, toasts, viewMode
```

---

## Component States (5-state rule)

Every interactive element implements: default → hover → focus → active → disabled

- **Button**: all 5 states with color and scale transitions
- **Input**: border color transitions on focus
- **PhotoTile**: overlay fade + selection ring + favorite indicator
- **NavLink**: background + text color on active/hover

---

## Accessibility

- All interactive elements use semantic HTML (`button`, `a`, `input`, `select`)
- `aria-label` on icon-only buttons
- `aria-live="polite"` on toast container
- Focus rings: `outline: 2px solid --color-accent`
- `prefers-reduced-motion`: opacity transitions instead of spring/slide
- Keyboard navigation in MediaViewer: `←/→/Escape/i`

---

## Performance

- **React Virtuoso**: virtual scrolling for gallery and timeline — only renders visible rows
- **Infinite queries**: pages loaded on demand, `overscan: 800-1000px`
- **Object URL lifecycle**: `fetchMediaBlob` returns managed URLs, revoked on unmount via `useRef`
- **Deferred value**: search uses `useDeferredValue` to avoid blocking input
- **Code splitting**: TanStack Router auto-splits every route
- **SHA-256 dedup**: client-side hash before upload, skips bytes if server has it

---

## Future Improvements

### High priority (unblocked by frontend)
1. **Trash restore** — add `DELETE /media/{id}/restore` to backend
2. **Device listing** — add `GET /devices` to backend
3. **Vault file browser** — complete E2EE upload handler + `GET /vaults/{id}/files`

### Medium priority
4. **Drag selection** — box-select multiple photos with pointer drag
5. **Date range filter UI** — date picker in gallery toolbar
6. **Albums** — group media into named collections (new backend feature needed)
7. **Download** — direct download button in viewer using `<a download>`
8. **Keyboard shortcuts overlay** — `?` to show all shortcuts

### Long-term
9. **PWA + offline** — service worker + offline-first media cache
10. **Native mobile** — React Native sharing same API client layer
11. **WebSocket sync** — real-time push instead of polling
12. **Map view** — GPS-tagged photos on a Leaflet/MapLibre map
13. **Face/object search** — hook into future ML backend feature
14. **Dark/light theme toggle** — `prefers-color-scheme` + manual override
