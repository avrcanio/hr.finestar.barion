# Check Item Entry QA Smoke Checklist

## Preconditions
- Android app build: latest `main`
- Backend with #21 endpoints deployed and reachable
- At least one active layout and one OPEN check available
- Test products exist and are active

## Smoke Scenarios

1. Open check and load items
- Open table from floor plan
- Verify `CheckScreen` loads item list without crash
- Verify totals block is visible (`subtotal`, `tax`, `total`)

2. Add item flow
- Tap `Dodaj stavku`
- Select product and qty
- Confirm add
- Verify new item appears in list
- Verify totals are updated

3. Update qty flow
- Tap `+` on existing item
- Verify qty increments and totals update
- Tap `-` and verify qty decrements

4. Remove item flow
- Delete one item
- Verify item disappears
- Verify totals recalculate

5. Empty check behavior
- Remove all items
- Verify `Naplata` button becomes disabled

6. Return to floor refresh
- Go back to floor plan
- Verify table status refreshes on resume
- If backend provides `item_count`, verify table card shows updated count

7. Network edge cases
- Simulate API timeout during add/update/remove
- Verify UI does not freeze
- Verify snackbar/error message is shown
- Verify existing item list remains stable (no corrupted state)

## Exit Criteria
- All smoke scenarios pass on one clean run
- No crashes or stuck loading states
- No inconsistent totals after mutations
