# Wallet UI Components

The `composeApp` module offers reusable Material components for building wallet screens.

## Components
- **BalanceCard** – shows SOL balance and token value with a loading state.
- **TransactionRow** – displays a single `SolanaTx` with colored amount indicator.
- **TokenChip** – compact token item with icon, ticker and balance.
- **PrimaryActionBar** – row of Send, Receive and Buy buttons.
- **QrAddressTextField** – text field with QR scanner icon and Base58 validation.
- **SeedInputField** – multiline seed phrase text field with validation.
- **SeedWordChip** – chip displaying single seed word.
- **GenerateSeedScreen** – screen for showing generated seed phrase.
- **ConfirmSeedScreen** – drag and drop seed verification screen.
- **ImportWalletScreen** – manual seed phrase input screen.
- **AccountHeader** – avatar with shortened public key and copy action.
- **AccountSettingsScreen** – basic settings list with export and logout options.
- **SeedRevealDialog** – modal dialog revealing the seed phrase.
- **OnboardingStepper** – simple step indicator for onboarding.

### Example usage
```kotlin
@Composable
fun WalletScreen() {
    Column {
        BalanceCard(solBalance = "1.0 SOL", tokensValue = "$150", isLoading = false)
        PrimaryActionBar(onSend = {}, onReceive = {}, onBuy = {})
        TransactionRow(tx = SolanaTx("sig", "Address", 0.1, incoming = true))
        TokenChip(icon = Icons.Default.Star, ticker = "SOL", balance = "1.0") {}
        QrAddressTextField(value = "", onValueChange = {}, onQrClick = {})
    }
}
```
