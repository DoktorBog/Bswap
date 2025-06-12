# Bswap Navigation

Navigation is powered by the multiplatform **Navigation 3** library. All targets share a single back stack stored in `rememberBackStack()`.

```
OnboardingWelcomeScreen --start--> ChoosePathScreen
  | "Create" --> GenerateSeedScreen --> ConfirmSeedScreen --done--> WalletHomeScreen
  | "Import" --> ImportWalletScreen -----------^
WalletHomeScreen --menu--> AccountSettingsScreen --"Logout"--> OnboardingWelcomeScreen
```

To move forward push a new `NavKey` onto the stack:

```kotlin
backStack.push(NavKey.GenerateSeed)
```
TODO: add GIF demo of the flow
