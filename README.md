# POS Finestar Barion (Android)

![Barion logo](logo.png)

Barion je tablet-first POS aplikacija za bar/klub rad: floor plan, otvoreni check kroz večer, dodavanje artikala po rundama i naplata/fiskalizacija na kraju.

## Trenutni status projekta

- Repo: `avrcanio/pos.finestar.barion`
- Android app package: `pos.finestar.barion`
- Trenutna app verzija: `versionCode=42`, `versionName=1.042`
- Backend base URL (default): `https://mozart.sibenik1983.hr/`

## Issue status (GitHub)

Pregledano je svih 31 issue-a.

- Zatvoreno: `#1-#4, #6, #10-#24, #26-#28, #30, #31`
- Otvoreno: `#25` (Auth/PIN hardening checklist), `#29` (multi-layout access + floor switch backend policy)
- Duplikati/zatvoreno: `#5, #7-#9, #11-#18`

### Što je isporučeno kroz issue-e

- `#1-#6`: Compose osnova, navigacija, floor canvas, check screen.
- `#19-#24`: Add-items epic (read flow, add/update/remove, totals, UX dorade).
- `#25`: PIN login + `/api/me` bootstrap + token session + interceptor + step-up verify za naplatu.
- `#26`: model rundi (`round_number`, `sent_to_bar`, `sent_at`) i slanje runde na šank.
- `#28`: Add Item split UX (kategorije + grid artikala sa slikama), košarica i round flow.
- `#30`: Storno/Gratis/Otpis flow s audit tragom i UI označavanjem.
- `#31`: deterministički products query (`sort=popular`) i limitiranje listanja (`limit=100`).

## Funkcionalnosti u aplikaciji

- Auth gate + PIN login ekran.
- Session persistence (DataStore), auto-bootstrap preko `/api/me/`.
- Floor plan prikaz stolova s layout switch chipovima.
- Otvaranje/učitavanje checka po stolu.
- Check pregled po rundama (`R1`, `R2`, `NEW`) + subtotal/tax/total.
- Long press na stavku: `Storno`, `Gratis`, `Otpis` (qty + reason).
- Add items ekran:
  - kategorije (`level=2`),
  - grid artikala sa slikama,
  - modal za količinu,
  - košarica + slanje runde.
- FREE flow: kad je total `0.00`, gumb `Naplata` postaje `Free` i zatvara check bez PIN-a.
- Payment Sprint 1:
  - izbor `Kompletna naplata` vs `Naplati dio (Split)`,
  - split wizard s remaining qty guardovima,
  - split summary s part statusima (`PREPARED/PAID/FAILED`),
  - per-part pay (`Gotovina` / `Kartica` confirm),
  - close check guard tek kad su svi partovi plaćeni i remaining qty = 0.
- Splash screen s prilagođenim brendom.

## API endpointi koje Android koristi

### Auth

- `POST /api/pos/pin/login/`
- `POST /api/pos/pin/verify/`
- `GET /api/me/`

### Floor/layout

- `GET /api/pos/active-layout/`
- `GET /api/pos/layouts/allowed/`
- `GET /api/pos/table-status/?layout_id=...`

### Katalog

- `GET /api/drink-categories/?level=2`
- `GET /api/pos/drink-categories/display/`
- `GET /api/pos/products/search/?drink_category_id=...&q=...&limit=100&sort=popular`
- fallback/legacy podrška: `GET /api/artikli/`

### Check + items

- `POST /api/pos/checks/`
- `GET /api/pos/checks/?table_id=...`
- `GET /api/pos/checks/{check_id}/items/`
- `POST /api/pos/checks/{check_id}/items/`
- `PATCH /api/pos/check-items/{item_id}/`
- `DELETE /api/pos/check-items/{item_id}/`
- `POST /api/pos/check-items/{item_id}/storno/`
- `POST /api/pos/check-items/{item_id}/gratis/`
- `POST /api/pos/check-items/{item_id}/otpis/`
- `POST /api/pos/checks/{check_id}/send-to-bar/`
- `POST /api/pos/checks/{check_id}/close/`
- `POST /api/pos/checks/{check_id}/issue-receipt/`

### Settlement (Payment Sprint 1)

- `POST /api/pos/checks/{check_id}/settlements/parts/prepare/`
- `POST /api/pos/checks/{check_id}/settlements/parts/{part_id}/pay-cash/`
- `POST /api/pos/checks/{check_id}/settlements/parts/{part_id}/pay-card/confirm/`

Cash contract napomena:
- `prepare` može i bez body-a (backend auto full CASH part).
- `pay-cash` trenutno backend očekuje i na `part_id=0` za auto-finalize cash flow.

## Cache i performanse (implementirano)

Implementiran je Room cache + SWR (stale-while-revalidate) za ključne endpointe:

- `/api/pos/active-layout/`
- `/api/pos/layouts/allowed/`
- `/api/drink-categories/`
- `/api/pos/products/search/`
- `/api/pos/checks/{id}/items/`

Strategija:

1. UI odmah dobiva cache ako postoji.
2. U pozadini ide network refresh (`forceRefresh=true`).
3. Kad stigne svježi payload, state se tiho ažurira.

Periodični cleanup cachea:

- WorkManager job `api_cache_cleanup_periodic`
- interval: svakih `24h` (initial delay `12h`)
- čišćenje: `deleteOlderThan(...)`, retention `3 dana`

## Tehnologije

- Kotlin, Jetpack Compose
- Hilt DI
- Retrofit + OkHttp
- Room (cache)
- WorkManager (periodični cleanup)
- DataStore (session)
- Coroutines + Flow

## Lokalni razvoj

### Build debug

```bash
./gradlew :app:assembleDebug
```

### Unit testovi

```bash
./gradlew :app:testDebugUnitTest
```

### Prod-like debug build script

```bash
scripts/build_prodNogmsDebug.sh
```

Napomena:

- Script očekuje signer secrets file preko `POS_SIGNER_SECRETS`.
- Ako nije postavljen, koristi default path u skripti.

## Struktura projekta

- `app/src/main/java/pos/finestar/barion/auth` - auth gate, PIN login, session bootstrap
- `app/src/main/java/pos/finestar/barion/floorplan` - floor rendering, layout selector
- `app/src/main/java/pos/finestar/barion/check` - check prikaz, round totals, storno/gratis/otpis, pay/free
- `app/src/main/java/pos/finestar/barion/additem` - pretraga artikala, kategorije, košarica, slanje runde
- `app/src/main/java/pos/finestar/barion/data/repo` - API + mapiranja + cache fallback
- `app/src/main/java/pos/finestar/barion/data/local` - Room cache + cleanup worker
- `app/src/main/java/pos/finestar/barion/domain` - modeli, repo ugovori, use-casevi
- `docs/qa` - QA checklist i contract test napomene

## Otvorene stavke

- `#25`: finalizirati preostali security checklist za sensitive akcije (policy/per-role coverage).
- `#29`: backend multi-layout access policy (user assignments/default/fallback) i dodatno poravnanje UX ponašanja.
- Payment Sprint 2:
  - tip UX (`0/5/10/custom`) i card tip calculation,
  - Viva callback hardening + retry UX,
  - full UAT scenariji za approved/declined/retry.

---

Ako želiš, mogu odmah nakon ovoga napraviti i `README` verziju na engleskom (za vanjske suradnike) i commitati obje verzije.
