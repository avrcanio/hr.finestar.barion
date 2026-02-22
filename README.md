# POS Finestar Barion

![Barion logo](logo.png)

Barion je POS sustav za klub/bar koji vodi cijeli tok rada od floor plana do računa na stolu.

## Što gradimo

Radimo **tablet-first POS aplikaciju** za osoblje koja omogućuje:

- prikaz aktivnog rasporeda stolova (floor plan)
- status svakog stola (slobodan / otvoren i dalje)
- tap na stol za otvaranje ili kreiranje računa
- ulazak u `CheckScreen` i rad sa stavkama računa

Cilj v1 je imati stabilan i brz operativni flow na šanku/sali, s backend-driven layoutom i jasnim check lifecycleom.

## Arhitektura (Android)

Projekt je organiziran kroz clean-ish module/packete:

- `ui/` navigacija + tema
- `floorplan/` floor rendering i interakcija sa stolovima
- `check/` check screen i state
- `domain/` modeli, repository sučelja, use-casevi
- `data/` repository implementacije + DI
- `api/` Retrofit API sloj

Tehnologije:

- Kotlin + Jetpack Compose
- Hilt (DI)
- Retrofit + OkHttp
- Gradle Kotlin DSL

## Trenutni status

Implementirano:

- Android projekt setup (`#2`)
- virtual canvas floor rendering (`#3`)
- `CheckScreen` skeleton + navigacija iz floor plana (`#6`)
- osnovni unit testovi za open/create check flow

Flow koji već radi u aplikaciji:

1. `FloorPlanScreen` rendera dummy stolove
2. klik na stol poziva open/create check logiku
3. navigacija vodi na `CheckScreen`

## Backend smjer (u tijeku)

Za backend integration (`#4`) ključni koraci su:

- `GET /pos/active-layout` (layout-only payload)
- modeli `Layout`, `Zone`, `Table`, `LayoutTable` placement
- mapiranje layout DTO -> Android domain model
- 404 kad nema aktivnog layouta
- auth/permissions za staff/device

Nakon toga ide live status sloj:

- `TableState` model
- `GET /pos/table-status?layout_id=...`

## Issue-driven razvoj

Repo se vodi preko GitHub issue-a i milestonea:

- Android milestone: **Barion v1 – Core Floor & Check Flow**
- Backend milestone: **Barion Backend v1 – POS Core API**

## Pokretanje lokalno

```bash
./gradlew :app:assembleDebug
```

Za unit testove:

```bash
./gradlew :app:testDebugUnitTest
```
