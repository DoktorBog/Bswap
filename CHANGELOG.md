# Changelog

## [Unreleased]
- Added `WalletDerivationStrategy` interface with default `Bip44WalletDerivationStrategy`.
- Mnemonic validation now enforces BIP-39 checksum.
- Solana keypair derivation uses canonical `m/44'/501'/<accountIndex>'/0'` path.
- Keys stored via `EncryptedSharedPreferences` on Android.
- Unit tests for mnemonic validation and multi-account derivation.
- Instrumentation test spins up local test validator for airdrop and balance check.
