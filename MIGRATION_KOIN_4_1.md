# Koin 4.1 Migration

This project now relies on Koin `4.1.0` and the new Compose APIs.

## Key changes

- DI start-up is handled by `KoinApplication` and `KoinMultiplatformApplication` composables.
- All manual `startKoin` calls were removed.
- ViewModel injection now uses `koinViewModel()` and previews wrap content in `KoinApplicationPreview`.
- Modules are defined in `appModule` and supplied directly to `KoinApplication` at the root composable.

## Sample usage

```kotlin
@Composable
fun BswapApp() {
    KoinApplication(application = { modules(appModule) }) {
        ComposeApp()
    }
}
```

Tests and previews should also be launched inside a `KoinApplicationPreview` block with test modules.
