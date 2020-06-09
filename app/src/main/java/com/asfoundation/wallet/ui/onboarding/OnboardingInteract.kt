package com.asfoundation.wallet.ui.onboarding

import com.appcoins.wallet.bdsbilling.WalletService
import com.appcoins.wallet.gamification.Gamification
import com.asfoundation.wallet.interact.CreateWalletInteract
import com.asfoundation.wallet.repository.PreferencesRepositoryType
import com.asfoundation.wallet.support.SupportInteractor

class OnboardingInteract(
    private val walletInteract: CreateWalletInteract,
    private val walletService: WalletService,
    private val preferencesRepositoryType: PreferencesRepositoryType,
    private val supportInteractor: SupportInteractor,
    private val gamificationRepository: Gamification) {

  fun getWalletAddress() = walletService.getWalletAddress()

  fun createWallet() =
      walletInteract.create()
          .map { it.address }
          .flatMap { address ->
            gamificationRepository.getUserStats(address)
                .doOnSuccess { supportInteractor.registerUser(it.level, address) }
                .map { address }
          }

  fun finishOnboarding() {
    preferencesRepositoryType.setOnboardingComplete()
  }

  fun clickSkipOnboarding() {
    preferencesRepositoryType.setOnboardingSkipClicked()
  }

  fun hasClickedSkipOnboarding() = preferencesRepositoryType.hasClickedSkipOnboarding()

  fun hasOnboardingCompleted() = preferencesRepositoryType.hasCompletedOnboarding()
}
