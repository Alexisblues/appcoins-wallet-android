package com.asfoundation.wallet.di

import android.content.Context
import android.os.Build
import com.appcoins.wallet.appcoins.rewards.repository.backend.BackendApi
import com.appcoins.wallet.bdsbilling.Billing
import com.appcoins.wallet.bdsbilling.BillingPaymentProofSubmission
import com.appcoins.wallet.bdsbilling.ProxyService
import com.appcoins.wallet.bdsbilling.WalletService
import com.appcoins.wallet.bdsbilling.repository.BdsApiSecondary
import com.appcoins.wallet.bdsbilling.repository.RemoteRepository.BdsApi
import com.appcoins.wallet.commons.MemoryCache
import com.appcoins.wallet.gamification.repository.GamificationApi
import com.appcoins.wallet.gamification.repository.entity.PromotionsDeserializer
import com.appcoins.wallet.gamification.repository.entity.PromotionsResponse
import com.appcoins.wallet.gamification.repository.entity.PromotionsSerializer
import com.aptoide.apk.injector.extractor.domain.IExtract
import com.asf.appcoins.sdk.contractproxy.AppCoinsAddressProxySdk
import com.asf.wallet.BuildConfig
import com.asfoundation.wallet.AirdropService
import com.asfoundation.wallet.advertise.CampaignInteract
import com.asfoundation.wallet.analytics.AnalyticsAPI
import com.asfoundation.wallet.apps.Applications
import com.asfoundation.wallet.billing.partners.*
import com.asfoundation.wallet.billing.purchase.LocalPaymentsLinkRepository.DeepLinkApi
import com.asfoundation.wallet.billing.share.BdsShareLinkRepository.BdsShareLinkApi
import com.asfoundation.wallet.entity.TransactionBuilder
import com.asfoundation.wallet.interact.DefaultTokenProvider
import com.asfoundation.wallet.interact.GetDefaultWalletBalanceInteract
import com.asfoundation.wallet.interact.SendTransactionInteract
import com.asfoundation.wallet.interact.WalletCreatorInteract
import com.asfoundation.wallet.poa.*
import com.asfoundation.wallet.repository.*
import com.asfoundation.wallet.service.*
import com.asfoundation.wallet.service.AutoUpdateService.AutoUpdateApi
import com.asfoundation.wallet.service.CampaignService.CampaignApi
import com.asfoundation.wallet.service.LocalCurrencyConversionService.TokenToLocalFiatApi
import com.asfoundation.wallet.service.TokenRateService.TokenToFiatApi
import com.asfoundation.wallet.topup.TopUpValuesApiResponseMapper
import com.asfoundation.wallet.topup.TopUpValuesService
import com.asfoundation.wallet.topup.TopUpValuesService.TopUpValuesApi
import com.asfoundation.wallet.ui.AppcoinsApps
import com.asfoundation.wallet.util.DeviceInfo
import com.asfoundation.wallet.wallet_blocked.WalletStatusApi
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.internal.schedulers.ExecutorScheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Named
import javax.inject.Singleton

@Module
class ServiceModule {

  @Provides
  @Named("BUY_SERVICE_ON_CHAIN")
  fun provideBuyServiceOnChain(sendTransactionInteract: SendTransactionInteract,
                               errorMapper: ErrorMapper,
                               @Named("wait_pending_transaction")
                               pendingTransactionService: TrackTransactionService,
                               defaultTokenProvider: DefaultTokenProvider,
                               countryCodeProvider: CountryCodeProvider, dataMapper: DataMapper,
                               addressService: AddressService): BuyService {
    return BuyService(WatchedTransactionService(object : TransactionSender {
      override fun send(transactionBuilder: TransactionBuilder): Single<String> {
        return sendTransactionInteract.buy(transactionBuilder)
      }
    }, MemoryCache(BehaviorSubject.create(), ConcurrentHashMap()), errorMapper, Schedulers.io(),
        pendingTransactionService), NoValidateTransactionValidator(), defaultTokenProvider,
        countryCodeProvider, dataMapper, addressService)
  }

  @Provides
  @Named("BUY_SERVICE_BDS")
  fun provideBuyServiceBds(sendTransactionInteract: SendTransactionInteract,
                           errorMapper: ErrorMapper,
                           bdsPendingTransactionService: BdsPendingTransactionService,
                           billingPaymentProofSubmission: BillingPaymentProofSubmission,
                           defaultTokenProvider: DefaultTokenProvider,
                           countryCodeProvider: CountryCodeProvider, dataMapper: DataMapper,
                           addressService: AddressService): BuyService {
    return BuyService(WatchedTransactionService(object : TransactionSender {
      override fun send(transactionBuilder: TransactionBuilder): Single<String> {
        return sendTransactionInteract.buy(transactionBuilder)
      }
    }, MemoryCache(BehaviorSubject.create(), ConcurrentHashMap()), errorMapper, Schedulers.io(),
        bdsPendingTransactionService),
        BuyTransactionValidatorBds(sendTransactionInteract, billingPaymentProofSubmission,
            defaultTokenProvider, addressService), defaultTokenProvider, countryCodeProvider,
        dataMapper, addressService)
  }

  @Provides
  fun provideAllowanceService(web3jProvider: Web3jProvider,
                              defaultTokenProvider: DefaultTokenProvider): AllowanceService {
    return AllowanceService(web3jProvider.default, defaultTokenProvider)
  }

  @Singleton
  @Provides
  @Named("IN_APP_PURCHASE_SERVICE")
  fun provideInAppPurchaseService(@Named("APPROVE_SERVICE_BDS") approveService: ApproveService,
                                  allowanceService: AllowanceService,
                                  @Named("BUY_SERVICE_BDS") buyService: BuyService,
                                  balanceService: BalanceService,
                                  errorMapper: ErrorMapper): InAppPurchaseService {
    return InAppPurchaseService(MemoryCache(BehaviorSubject.create(), HashMap()), approveService,
        allowanceService, buyService, balanceService, Schedulers.io(), errorMapper)
  }

  @Singleton
  @Provides
  @Named("ASF_IN_APP_PURCHASE_SERVICE")
  fun provideInAppPurchaseServiceAsf(
      @Named("APPROVE_SERVICE_ON_CHAIN") approveService: ApproveService,
      allowanceService: AllowanceService, @Named("BUY_SERVICE_ON_CHAIN") buyService: BuyService,
      balanceService: BalanceService, errorMapper: ErrorMapper): InAppPurchaseService {
    return InAppPurchaseService(MemoryCache(BehaviorSubject.create(), HashMap()), approveService,
        allowanceService, buyService, balanceService, Schedulers.io(), errorMapper)
  }

  @Singleton
  @Provides
  fun providesBdsTransactionService(billing: Billing,
                                    billingPaymentProofSubmission: BillingPaymentProofSubmission): BdsTransactionService {
    return BdsTransactionService(Schedulers.io(), MemoryCache(BehaviorSubject.create(), HashMap()),
        CompositeDisposable(),
        BdsPendingTransactionService(billing, Schedulers.io(), 5, billingPaymentProofSubmission))
  }

  @Singleton
  @Provides
  fun provideProofOfAttentionService(
      hashCalculator: HashCalculator, proofWriter: ProofWriter,
      disposables: TaggedCompositeDisposable,
      @Named("MAX_NUMBER_PROOF_COMPONENTS") maxNumberProofComponents: Int,
      countryCodeProvider: CountryCodeProvider,
      addressService: AddressService,
      walletService: WalletService,
      campaignInteract: CampaignInteract): ProofOfAttentionService {
    return ProofOfAttentionService(MemoryCache(BehaviorSubject.create(), HashMap()),
        BuildConfig.APPLICATION_ID, hashCalculator, CompositeDisposable(), proofWriter,
        Schedulers.computation(), maxNumberProofComponents, BackEndErrorMapper(), disposables,
        countryCodeProvider, addressService, walletService,
        campaignInteract)
  }

  @Provides
  fun provideAirdropService(client: OkHttpClient, gson: Gson): AirdropService {
    val api = Retrofit.Builder()
        .baseUrl(AirdropService.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(AirdropService.Api::class.java)
    return AirdropService(api, gson, Schedulers.io())
  }

  @Singleton
  @Provides
  fun provideTokenRateService(client: OkHttpClient, objectMapper: ObjectMapper): TokenRateService {
    val baseUrl = TokenRateService.CONVERSION_HOST
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(TokenToFiatApi::class.java)
    return TokenRateService(api)
  }

  @Singleton
  @Provides
  fun provideLocalCurrencyConversionService(client: OkHttpClient,
                                            objectMapper: ObjectMapper): LocalCurrencyConversionService {
    val baseUrl = LocalCurrencyConversionService.CONVERSION_HOST
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(TokenToLocalFiatApi::class.java)
    return LocalCurrencyConversionService(api)
  }

  @Singleton
  @Provides
  fun provideCurrencyConversionService(tokenRateService: TokenRateService,
                                       localCurrencyConversionService: LocalCurrencyConversionService): CurrencyConversionService {
    return CurrencyConversionService(tokenRateService, localCurrencyConversionService)
  }

  @Singleton
  @Provides
  fun provideAccountWalletService(accountKeyService: AccountKeystoreService,
                                  passwordStore: PasswordStore,
                                  walletCreatorInteract: WalletCreatorInteract,
                                  walletRepository: WalletRepositoryType,
                                  syncScheduler: ExecutorScheduler): WalletService {
    return AccountWalletService(accountKeyService, passwordStore, walletCreatorInteract,
        SignDataStandardNormalizer(), walletRepository, syncScheduler)
  }

  @Singleton
  @Provides
  fun provideProxyService(proxySdk: AppCoinsAddressProxySdk): ProxyService {
    return object : ProxyService {
      private val NETWORK_ID_ROPSTEN = 3
      private val NETWORK_ID_MAIN = 1
      override fun getAppCoinsAddress(debug: Boolean): Single<String> {
        return proxySdk.getAppCoinsAddress(if (debug) NETWORK_ID_ROPSTEN else NETWORK_ID_MAIN)
      }

      override fun getIabAddress(debug: Boolean): Single<String> {
        return proxySdk.getIabAddress(if (debug) NETWORK_ID_ROPSTEN else NETWORK_ID_MAIN)
      }
    }
  }

  @Singleton
  @Provides
  fun provideBdsPendingTransactionService(
      billingPaymentProofSubmission: BillingPaymentProofSubmission,
      billing: Billing): BdsPendingTransactionService {
    return BdsPendingTransactionService(billing, Schedulers.io(), 5, billingPaymentProofSubmission)
  }

  @Singleton
  @Provides
  fun providePoASubmissionService(client: OkHttpClient): CampaignService {
    val baseUrl = CampaignService.SERVICE_HOST
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(CampaignApi::class.java)
    return CampaignService(api, BuildConfig.VERSION_CODE, Schedulers.io())
  }

  @Singleton
  @Provides
  fun providesAddressService(installerService: InstallerService,
                             addressService: WalletAddressService,
                             oemIdExtractorService: OemIdExtractorService): AddressService {
    return PartnerAddressService(installerService, addressService,
        DeviceInfo(Build.MANUFACTURER, Build.MODEL), oemIdExtractorService)
  }

  @Singleton
  @Provides
  fun providesInstallerService(context: Context): InstallerService {
    return InstallerSourceService(context)
  }

  @Singleton
  @Provides
  fun providesWalletAddressService(api: BdsPartnersApi): WalletAddressService {
    return PartnerWalletAddressService(api, BuildConfig.DEFAULT_STORE_ADDRESS,
        BuildConfig.DEFAULT_OEM_ADDRESS)
  }

  @Singleton
  @Provides
  fun providesTopUpValuesService(topUpValuesApi: TopUpValuesApi,
                                 responseMapper: TopUpValuesApiResponseMapper): TopUpValuesService {
    return TopUpValuesService(topUpValuesApi, responseMapper)
  }

  @Singleton
  @Provides
  fun provideGasService(client: OkHttpClient, gson: Gson): GasService {
    return Retrofit.Builder()
        .baseUrl(GasService.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(GasService::class.java)
  }

  @Singleton
  @Provides
  fun provideOemIdExtractorService(context: Context, extractor: IExtract): OemIdExtractorService {
    return OemIdExtractorService(OemIdExtractorV1(context),
        OemIdExtractorV2(context, extractor))
  }

  @Provides
  fun provideAutoUpdateService(autoUpdateApi: AutoUpdateApi) =
      AutoUpdateService(autoUpdateApi)

  @Provides
  fun provideBalanceService(
      getDefaultWalletBalanceInteract: GetDefaultWalletBalanceInteract): BalanceService {
    return getDefaultWalletBalanceInteract
  }

  @Provides
  @Named("APPROVE_SERVICE_BDS")
  fun provideApproveServiceBds(sendTransactionInteract: SendTransactionInteract,
                               errorMapper: ErrorMapper, @Named("no_wait_transaction")
                               noWaitPendingTransactionService: TrackTransactionService,
                               billingPaymentProofSubmission: BillingPaymentProofSubmission,
                               addressService: AddressService): ApproveService {
    return ApproveService(WatchedTransactionService(object : TransactionSender {
      override fun send(transactionBuilder: TransactionBuilder): Single<String> {
        return sendTransactionInteract.approve(transactionBuilder)
      }
    }, MemoryCache(BehaviorSubject.create(), ConcurrentHashMap()), errorMapper, Schedulers.io(),
        noWaitPendingTransactionService),
        ApproveTransactionValidatorBds(sendTransactionInteract, billingPaymentProofSubmission,
            addressService))
  }

  @Singleton
  @Provides
  fun provideWalletBalanceService(client: OkHttpClient, gson: Gson): WalletBalanceService {
    return Retrofit.Builder()
        .baseUrl(WalletBalanceService.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(WalletBalanceService::class.java)
  }

  @Singleton
  @Provides
  @Named("no_wait_transaction")
  fun providesNoWaitTransactionTransactionTrackTransactionService(): TrackTransactionService {
    return NotTrackTransactionService()
  }


  @Singleton
  @Provides
  fun providesPendingTransactionService(web3jService: Web3jService): PendingTransactionService {
    return PendingTransactionService(web3jService, Schedulers.computation(), 5)
  }

  @Singleton
  @Provides
  @Named("wait_pending_transaction")
  fun providesWaitPendingTransactionTrackTransactionService(
      pendingTransactionService: PendingTransactionService): TrackTransactionService {
    return TrackPendingTransactionService(pendingTransactionService)
  }

  @Singleton
  @Provides
  fun provideAccountKeyStoreService(context: Context): AccountKeystoreService {
    val file = File(context.filesDir, "keystore/keystore")
    return Web3jKeystoreAccountService(KeyStoreFileManager(file.absolutePath, ObjectMapper()),
        ObjectMapper())
  }

  @Singleton
  @Provides
  fun providesWeb3jService(web3jProvider: Web3jProvider): Web3jService {
    return Web3jService(web3jProvider)
  }


  @Singleton
  @Provides
  fun provideSmsValidationApi(client: OkHttpClient, gson: Gson): SmsValidationApi {
    val baseUrl = BuildConfig.BACKEND_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(SmsValidationApi::class.java)
  }

  @Singleton
  @Provides
  fun provideWalletStatusApi(client: OkHttpClient, gson: Gson): WalletStatusApi {
    val baseUrl = BuildConfig.BACKEND_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(WalletStatusApi::class.java)
  }

  @Singleton
  @Provides
  fun provideDeepLinkApi(client: OkHttpClient, gson: Gson): DeepLinkApi {
    val baseUrl = BuildConfig.CATAPPULT_BASE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(DeepLinkApi::class.java)
  }

  @Singleton
  @Provides
  fun providesTopUpValuesApi(client: OkHttpClient, gson: Gson): TopUpValuesApi {
    val baseUrl = BuildConfig.CATAPPULT_BASE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(TopUpValuesApi::class.java)
  }

  @Singleton
  @Provides
  fun provideBdsShareLinkApi(client: OkHttpClient, gson: Gson): BdsShareLinkApi {
    val baseUrl = BuildConfig.CATAPPULT_BASE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(BdsShareLinkApi::class.java)
  }

  @Singleton
  @Provides
  fun provideBdsPartnersApi(client: OkHttpClient, gson: Gson): BdsPartnersApi {
    val baseUrl = BuildConfig.BASE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(BdsPartnersApi::class.java)
  }

  @Singleton
  @Provides
  fun provideAnalyticsAPI(client: OkHttpClient, objectMapper: ObjectMapper): AnalyticsAPI {
    return Retrofit.Builder()
        .baseUrl("https://ws75.aptoide.com/api/7/")
        .client(client)
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(AnalyticsAPI::class.java)
  }

  @Provides
  fun provideGamificationApi(client: OkHttpClient): GamificationApi {
    val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm")
        .registerTypeAdapter(PromotionsResponse::class.java, PromotionsSerializer())
        .registerTypeAdapter(PromotionsResponse::class.java, PromotionsDeserializer())
        .create()
    val baseUrl = CampaignService.SERVICE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(GamificationApi::class.java)
  }

  @Singleton
  @Provides
  fun provideBackendApi(client: OkHttpClient, gson: Gson): BackendApi {
    return Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_HOST)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(BackendApi::class.java)
  }

  @Singleton
  @Provides
  fun provideAppcoinsApps(client: OkHttpClient, gson: Gson): AppcoinsApps {
    val appsApi = Retrofit.Builder()
        .baseUrl(AppsApi.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(AppsApi::class.java)
    return AppcoinsApps(Applications.Builder()
        .setApi(BDSAppsApi(appsApi))
        .build())
  }

  @Singleton
  @Provides
  fun provideBdsApi(client: OkHttpClient, gson: Gson): BdsApi {
    val baseUrl = BuildConfig.BASE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(BdsApi::class.java)
  }

  @Singleton
  @Provides
  fun provideBdsApiSecondary(client: OkHttpClient, gson: Gson): BdsApiSecondary {
    val baseUrl = BuildConfig.BDS_BASE_HOST
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()
        .create(BdsApiSecondary::class.java)
  }

}