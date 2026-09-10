# Design QA

## Comparison target

- Source visual truth: `/var/folders/q2/_3qtdb911zx8jw1gqhn2b6jw0000gn/T/codex-clipboard-2cab3291-f114-4abd-9d09-06be7a67f01f.png`
- Source dimensions: 560 × 1544 px; target state is the exam waiting-page model configuration drawer.
- Implementation screenshot: `/Users/apple/Desktop/aisaas-考试使用版/design-qa-login-state.png`
- Implementation dimensions: 1280 × 720 CSS px; browser viewport 1280 × 720, device scale factor not overridden.
- Implementation state: local Vite preview at `/web/#/login`.

## Evidence

The implementation could not be opened in the exam waiting state because the local application requires an authenticated candidate session. No account or password was guessed or entered. The login-state screenshot is therefore not a valid same-state visual comparison with the source drawer.

Focused region comparison was not performed because the target drawer was not reachable without authentication.

## Findings

- [BLOCKED] Candidate exam configuration state was not capturable.
  Location: `/exam/portal` → exam waiting page → model configuration drawer.
  Evidence: the browser stopped at `/web/#/login`; the source is the authenticated candidate drawer.
  Impact: typography, spacing, responsive layout, copy, and interaction state cannot be visually verified in-browser.
  Fix: open the local preview with a valid candidate session and recapture the waiting-page drawer at 560 × 1544 px.

## Primary interactions tested

- Local preview server started successfully.
- Vite main build completed successfully.
- Candidate authenticated flow: blocked by login.
- Model menu redirect and drawer opening: not browser-tested because candidate authentication was unavailable.
- Console error check for the target state: not available because the target state was not reached.

## Final result

blocked
