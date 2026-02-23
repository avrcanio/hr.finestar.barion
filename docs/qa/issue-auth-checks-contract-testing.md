# Issue QA Checklist: Auth + Checks Contract

## Scope
- Verify DRF token auth header format in Android client (`Authorization: Token <token>`)
- Verify checks API contract:
- `POST /api/pos/checks/` expects JSON body `{ "table_id": <id> }`
- `GET /api/pos/checks/?table_id=<id>` expects query param
- Verify app handles `400` responses without crash and shows backend error message

## Preconditions
- App build includes latest networking fixes
- Backend reachable (same environment as mobile app)
- Valid PIN for login test user
- At least one valid table id available in active layout

## API Quick Checks (cURL)

1. Login and extract token
```bash
curl -i -X POST "$BASE/api/pos/pin/login/" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"pin":"<PIN>"}'
```

2. Create check (body table_id)
```bash
curl -i -X POST "$BASE/api/pos/checks/" \
  -H "Authorization: Token <TOKEN>" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"table_id":123}'
```

3. Open existing check (query table_id)
```bash
curl -i "$BASE/api/pos/checks/?table_id=123" \
  -H "Authorization: Token <TOKEN>" \
  -H "Accept: application/json"
```

## Test Cases

1. Auth header accepted
- Trigger any protected POS endpoint from app
- Expected: no `401` if token is valid

2. Missing/invalid token rejected
- Call protected endpoint without/with invalid token
- Expected: `401 Unauthorized`

3. Create check happy path
- `POST /api/pos/checks/` with valid JSON body `{"table_id": <valid>}`
- Expected: `200/201` and response contains `check`

4. Create check missing body field
- `POST /api/pos/checks/` with empty `{}` or missing `table_id`
- Expected: `400` and clear backend `detail`/validation message

5. Create check with query-only param
- `POST /api/pos/checks/?table_id=<id>` without JSON body
- Expected: reject with `400` if backend strictly reads body

6. Open check happy path
- `GET /api/pos/checks/?table_id=<valid>`
- Expected: `200` and check payload

7. Open check missing query param
- `GET /api/pos/checks/` without `table_id`
- Expected: `400` with message that `table_id` is required

8. Android no-crash on backend 400
- Force backend `400` (invalid table id or missing param)
- Expected: app does not crash
- Expected: user sees backend message (parsed from error body) in UI

9. Regression smoke
- Login with PIN
- Open floor plan
- Open table/check
- Add item
- Issue receipt with PIN verify
- Expected: no regressions in normal flow

## Exit Criteria
- All 9 test cases executed
- No app crash on `400` or validation failures
- Error messages are actionable and match backend response
