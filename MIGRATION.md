# Migration Guide

This release removes the `BswapTheme` file introduced earlier. All components
continue to use `UiTheme` which now defines shapes and colours directly.

## Updating Buttons

`UiButton` now renders a FilledTonal or Outlined style depending on the
`secondary` parameter. Ensure custom buttons pass `secondary = true` when a
secondary action is required.

## Onboarding Flow

The welcome screen now presents a swipeable carousel with "Create Wallet" and
"Import Wallet" actions. Navigation keys remain unchanged.
