# Bswap Navigation

Navigation is powered by the multiplatform **Navigation&nbsp;3** library. All platforms
use the same `NavKey` sealed hierarchy and a single back stack returned by
`rememberBackStack()`.

Each screen receives the back stack so it can push new keys or pop the current
one. This approach works on Android, Desktop and WASM/JS without any platform
specific code.

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

The back stack persists across configuration changes thanks to
`rememberSaveable`, so your screen state survives rotations automatically.
