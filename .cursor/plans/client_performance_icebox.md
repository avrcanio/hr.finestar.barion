# Icebox: Barion client performance (follow-up)

Niski prioritet ili ovisi o backendu / većem UX radu. Nije blokiralo zatvaranje glavnog performans plana (`barion_client_performance_0857bd89.plan.md` u Cursor plans, označen ZATVORENO).

## 1. Brotli (`br`) za JSON

**Uvjet:** Helsinki (ili reverse proxy) stvarno vraća `Content-Encoding: br` za REST odgovore koje Barion često vuče.

**Koraci (grubo):**

- Potvrditi curlom / OkHttp logom postoji li `br`.
- Dodati OkHttp Brotli interceptor (npr. `okhttp-brotli` ili službena integracija uz verziju OkHttp 5).
- Mjeriti veličinu odgovora prije/poslije na tipičnim endpointima (bootstrap, delta).

**Ako backend šalje samo gzip:** nema dobitka — preskočiti.

## 2. Shared element transitions

**Cilj:** vizualno “instant” prijelaz npr. floor plan → check / add item.

**Zašto icebox:** zahtijeva koordinaciju `NavHost` ruta, verzije Compose/Material, i dizajn (koji element dijeli granicu); veći PR od cache/keys.

**Koraci (grubo):**

- Odabrati 1–2 prijelaza s najvećim dojmom.
- Uskladiti s `AnimatedContent` / shared bounds API za trenutni Compose BOM.

---

*Kad uzmeš stavku s iceboxa, obriši je ovdje ili premjesti u aktivan plan.*
