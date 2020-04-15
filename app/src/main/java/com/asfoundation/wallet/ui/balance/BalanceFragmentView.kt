package com.asfoundation.wallet.ui.balance

import android.view.View
import com.asfoundation.wallet.util.WalletCurrency
import io.reactivex.Observable

interface BalanceFragmentView {

  fun setupUI()

  fun updateTokenValue(tokenBalance: TokenBalance)

  fun updateOverallBalance(overallBalance: FiatValue)

  fun getCreditClick(): Observable<View>

  fun getAppcClick(): Observable<View>

  fun getEthClick(): Observable<View>

  fun showTokenDetails(view: View)

  fun getTopUpClick(): Observable<Any>

  fun showTopUpScreen()

  fun getCopyClick(): Observable<Any>

  fun getQrCodeClick(): Observable<Any>

  fun setWalletAddress(walletAddress: String)

  fun setAddressToClipBoard(walletAddress: String)

  fun showQrCodeView()

  fun collapseBottomSheet()

  fun backPressed(): Observable<Any>

  fun handleBackPress()

  fun showWalletCreatedAnimation()

  fun showCreatingAnimation()
}
