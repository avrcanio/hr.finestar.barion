# POS API — zajednički referentni ugovor

Ovaj dokument povezuje **Android klijent** i **Django backend**. Kanonski popis HTTP ruta na klijentu je Retrofit sučelje u:

`app/src/main/java/hr/finestar/barion/api/PosApi.kt`

Backend POS logika živi u repozitoriju [caffe-bar-managment](https://github.com/avrcanio/caffe-bar-managment), tipično pod `app/pos/` (URL-i u `app/config/`).

## Grupa ruta (sažetak)

| Područje | Primjer putanje |
|----------|-----------------|
| Auth / sesija | `POST /api/pos/pin/login/`, `POST /api/pos/pin/verify/`, `GET /api/me/` |
| Floor / layout | `GET /api/pos/active-layout/`, `GET /api/pos/layouts/allowed/`, `GET /api/pos/table-status/`, `GET /api/pos/runtime-mode/` |
| Katalog | `GET /api/pos/bootstrap/`, `GET /api/pos/catalog/changes/`, `GET /api/pos/categories/display/`, `GET /api/pos/products/search/`, modifiers, bundle-price |
| Check | `POST/GET /api/pos/checks/...`, stavke, storno/gratis/otpis, `send-to-bar`, `close`, `issue-receipt` |
| Naplata | `prepare-settlement`, `pay-cash`, `pay-card/confirm`, `settlement-state`, `round-state`, `fiscalize` |

Za detalje tijela odgovora i napomene o ponašanju vidi i **README** u Android repou (sekcija API endpointi).

## Push (FCM)

- Android se pretplaćuje na topic **`barion_catalog`** (kad je `CATALOG_FCM_ENABLED=true`).
- Očekivani data payload za delta sync parsira se u `CatalogChangedPushParser` / `BarionFirebaseMessagingService`.
- Konfiguracija projekta: Firebase isti kao u uputama u Android README (FCM lokalni setup).

## Verzioniranje

Kada mijenjaš ugovor (nova polja, nove rute), ažuriraj **oba** repozitorija ili u istom PR-u (ako monorepo) ili uz jasnu referencu u commit porukama / issueu povezanom s oba repoa.
