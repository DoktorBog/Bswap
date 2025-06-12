# Bswap Navigation

This module uses `navigation-compose` on Android and a simple state driven router on Desktop and Web.

```
OnboardingWelcomeScreen --start--> ChoosePathScreen
  | "Create" --> GenerateSeedScreen --> ConfirmSeedScreen --done--> WalletHomeScreen
  | "Import" --> ImportWalletScreen -----------^
WalletHomeScreen --menu--> AccountSettingsScreen --"Logout"--> OnboardingWelcomeScreen
```

Example of navigating to `GenerateSeedScreen` from the welcome screen:

```kotlin
navController.navigateToGenerateSeed()
```
TODO: add GIF demo of the flow
