package com.bswap.navigation

import androidx.navigation.NavHostController
/**
 * Enumeration of all application routes.
 */
sealed class NavRoute(val route: String) {
    data object OnboardWelcome : NavRoute(ONBOARD_WELCOME)
    data object OnboardChoose : NavRoute(ONBOARD_CHOOSE)
    data object GenerateSeed : NavRoute(GENERATE_SEED)
    data object ConfirmSeed : NavRoute(CONFIRM_SEED)
    data object ImportWallet : NavRoute(IMPORT_WALLET)
    data object WalletHome : NavRoute(WALLET_HOME)
    data object AccountSettings : NavRoute(ACCOUNT_SETTINGS)

    companion object {
        /** Argument name containing seed phrase when navigating to [CONFIRM_SEED]. */
        const val ARG_SEED = "seed"
        const val ONBOARD_WELCOME = "onboard_welcome"
        const val ONBOARD_CHOOSE = "onboard_choose"
        const val GENERATE_SEED = "generate_seed"
        const val CONFIRM_SEED = "confirm_seed"
        const val IMPORT_WALLET = "import_wallet"
        const val WALLET_HOME = "wallet_home"
        const val ACCOUNT_SETTINGS = "account_settings"

        /**
         * Build route to [WalletHome] with [publicKey].
         *
         * @param publicKey wallet public key
         * @return navigation path
         */
        fun walletHome(publicKey: String): String = "$WALLET_HOME/$publicKey"

        /**
         * Build route to [ConfirmSeed] using the given seed words. Words are
         * concatenated with '-' to form a single path segment.
         *
         * @param words ordered seed words
         */
        fun confirmSeed(words: List<String>): String =
            "$CONFIRM_SEED/" + words.joinToString("-")

        /**
         * Parse seed words from a [route] previously built with [confirmSeed].
         */
        fun parseSeed(route: String): List<String> =
            route.substringAfter("$CONFIRM_SEED/")
                .split('-')
                .filter { it.isNotBlank() }

        /** Convert raw string to [NavRoute]. */
        fun fromRoute(route: String?): NavRoute = when (route) {
            ONBOARD_WELCOME -> OnboardWelcome
            ONBOARD_CHOOSE -> OnboardChoose
            GENERATE_SEED -> GenerateSeed
            CONFIRM_SEED -> ConfirmSeed
            IMPORT_WALLET -> ImportWallet
            WALLET_HOME -> WalletHome
            ACCOUNT_SETTINGS -> AccountSettings
            else -> OnboardWelcome
        }

        /** Extension helpers for type-safe navigation. */
        fun NavHostController.navigateToOnboardWelcome(popUpTo: String? = null, inclusive: Boolean = false) {
            navigate(ONBOARD_WELCOME) {
                popUpTo?.let { popUpTo(it) { this.inclusive = inclusive } }
            }
        }

        fun NavHostController.navigateToOnboardChoose() = navigate(ONBOARD_CHOOSE)

        fun NavHostController.navigateToGenerateSeed() = navigate(GENERATE_SEED)

        fun NavHostController.navigateToImportWallet() = navigate(IMPORT_WALLET)

        fun NavHostController.navigateToConfirmSeed(words: List<String>) = navigate(confirmSeed(words))

        fun NavHostController.navigateToWalletHome(publicKey: String, popUpTo: String? = null, inclusive: Boolean = false) {
            navigate(walletHome(publicKey)) {
                popUpTo?.let { popUpTo(it) { this.inclusive = inclusive } }
            }
        }

        fun NavHostController.navigateToAccountSettings() = navigate(ACCOUNT_SETTINGS)
    }
}
