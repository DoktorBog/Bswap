# Bswap Navigation

This module uses the experimental **Navigation 3** API across platforms with a shared `Navigator` implementation.

```
OnboardingWelcomeScreen --start--> ChoosePathScreen
  | "Create" --> GenerateSeedScreen --> ConfirmSeedScreen --done--> WalletHomeScreen
  | "Import" --> ImportWalletScreen -----------^
WalletHomeScreen --menu--> AccountSettingsScreen --"Logout"--> OnboardingWelcomeScreen
```

Example of pushing the generate seed screen:

```kotlin
navigator.push(NavKey.GenerateSeed)
```
TODO: add GIF demo of the flow
