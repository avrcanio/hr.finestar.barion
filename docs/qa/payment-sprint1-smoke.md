# Payment Sprint 1 QA Smoke Checklist

## Preconditions
- Android app build: branch with Sprint 1 payment implementation
- Backend deployed with canonical settlement endpoints
- Backend base URL: `https://mozart.sibenik1983.hr`
- Test device: Xiaomi Redmi Note 13 Pro
- Test check with at least 3 normal items

## Covered scope
- Full vs Split entry choice
- Split wizard with qty constraints (`0..remaining`)
- Prepare part + per-part settlement (Cash/Card confirm)
- Split summary and per-part status
- Close check gating (allowed only when all remaining qty are settled)

## Backend contract note (cash)
- `POST /api/pos/checks/{checkId}/settlements/parts/prepare/` može raditi i bez body-a (auto full CASH part).
- Cash naplata ide na:
  - `POST /api/pos/checks/{checkId}/settlements/parts/0/pay-cash/`
  - payload: `{ \"amount\": \"23.36\" }`

## Smoke scenarios

1. Full payment entry
- Open check with items
- Tap `Naplata`
- Verify choice dialog shows:
  - `Kompletna naplata`
  - `Naplati dio (Split)`

2. Full payment via settlement
- Choose `Kompletna naplata`
- Verify method dialog appears for created full part
- Pay with `Gotovina`
- Verify success message and check refresh

3. Split wizard qty guards
- Choose `Naplati dio (Split)`
- Increase/decrease qty on items
- Verify selected qty never exceeds remaining qty
- Verify `Next` and `Plati sada` disabled when nothing selected

4. Split part creation (`Next`)
- Select subset of items and tap `Next`
- Verify new part is created
- Verify selected quantities are deducted from remaining

5. Split part immediate pay (`Plati sada`)
- Select subset and tap `Plati sada`
- Choose `Gotovina`
- Verify part status changes to `PAID`

6. Summary and per-part pay
- Open `Summary`
- Verify list of created parts and amounts
- For unpaid part, tap `Plati`
- Choose `Kartica`
- Verify part marked `PAID` (Sprint 1 uses backend card confirm with debug provider refs)

7. Close gating
- Before all parts are paid: verify `Close check` is disabled
- After all remaining qty are settled and all parts `PAID`: verify `Close check` enabled and successful

8. Error handling
- Simulate backend validation/network failure during prepare or pay
- Verify app shows message/snackbar
- Verify local split state stays consistent (no negative remaining)

## Exit criteria
- All smoke scenarios pass in one clean run on Redmi Note 13 Pro
- No crashes/stuck loading states
- No inconsistent remaining qty or duplicated part states
