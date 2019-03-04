package com.asfoundation.wallet.ui.iab;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import com.appcoins.wallet.bdsbilling.Billing;
import com.appcoins.wallet.bdsbilling.WalletService;
import com.asf.wallet.R;
import com.asfoundation.wallet.billing.TransactionService;
import com.asfoundation.wallet.billing.analytics.BillingAnalytics;
import com.asfoundation.wallet.billing.purchase.BillingFactory;
import com.asfoundation.wallet.entity.TransactionBuilder;
import dagger.android.support.DaggerFragment;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

import static com.asfoundation.wallet.analytics.FacebookEventLogger.EVENT_REVENUE_CURRENCY;
import static com.asfoundation.wallet.billing.analytics.BillingAnalytics.PAYMENT_METHOD_PAYPAL;

public class BillingWebViewFragment extends DaggerFragment {

  private static final String BILLING_SCHEMA = "billing://";

  private static final String URL = "url";
  private static final String CURRENT_URL = "currentUrl";
  private static TransactionBuilder transaction;

  @Inject Billing billing;
  @Inject BillingFactory billingFactory;
  @Inject WalletService walletService;
  @Inject TransactionService transactionService;

  private final AtomicReference<ScheduledFuture<?>> timeoutReference;
  private WebView webView;
  private ProgressBar webviewProgressBar;
  private String currentUrl;
  private ScheduledExecutorService executorService;
  @Inject InAppPurchaseInteractor inAppPurchaseInteractor;
  @Inject BillingAnalytics analytics;


  public static BillingWebViewFragment newInstance(String url, TransactionBuilder transactionBuilder) {
    Bundle args = new Bundle();
    args.putString(URL, url);
    BillingWebViewFragment fragment = new BillingWebViewFragment();
    fragment.setArguments(args);
    fragment.setRetainInstance(true);
    transaction = transactionBuilder;
    return fragment;
  }

  public BillingWebViewFragment() {
    this.timeoutReference = new AtomicReference<>();
  }

  private AndroidBug5497Workaround androidBug5497Workaround;

  @Override public void onAttach(Context context) {
    super.onAttach(context);

    androidBug5497Workaround = new AndroidBug5497Workaround(getActivity());
    androidBug5497Workaround.addListener();
  }

  @Override public void onDetach() {
    androidBug5497Workaround.removeListener();

    super.onDetach();
  }

  @Override public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    executorService = Executors.newScheduledThreadPool(0);

    if (getArguments() == null || !getArguments().containsKey(URL)) {
      throw new IllegalArgumentException("Provided url is null!");
    }

    if (savedInstanceState == null) {
      currentUrl = getArguments().getString(URL);
    } else {
      currentUrl = savedInstanceState.getString(CURRENT_URL);
    }

    CookieManager.getInstance()
        .setAcceptCookie(true);
  }

  @Nullable @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.webview_fragment, container, false);

    webView = view.findViewById(R.id.webview);
    webviewProgressBar = view.findViewById(R.id.webview_progress_bar);

    webView.setWebViewClient(new WebViewClient() {

      @Override public boolean shouldOverrideUrlLoading(WebView view, String clickUrl) {
        currentUrl = clickUrl;

        if (clickUrl.startsWith(BILLING_SCHEMA)) {
          Intent intent = new Intent(getContext(), IabActivity.class);
          intent.setData(Uri.parse(clickUrl));
          getActivity().setResult(WebViewActivity.SUCCESS);
          sendPaymentEvent();
          sendRevenueEvent();
          getActivity().finish();
          getContext().startActivity(intent);

          return true;
        } else {
          return false;
        }
      }

      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return super.shouldOverrideUrlLoading(view, request);
      }

      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);

        if (!url.contains("/redirect")) {
          ScheduledFuture<?> timeout = timeoutReference.getAndSet(null);
          if (timeout != null) {
            timeout.cancel(false);
          }
          webviewProgressBar.setVisibility(View.GONE);
        }
      }
    });

    WebSettings webSettings = webView.getSettings();
    webSettings.setJavaScriptEnabled(true);

    webView.loadUrl(currentUrl);

    return view;
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if (savedInstanceState == null) {
      sendPaymentMethodDetailsEvent();
    }
  }

  @Override public void onDestroy() {
    executorService.shutdown();

    super.onDestroy();
  }

  @Override public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putString(CURRENT_URL, currentUrl);
  }

  public void sendPaymentMethodDetailsEvent() {
    analytics.sendPaymentMethodDetailsEvent(transaction.getDomain(), transaction.getSkuId(),
        transaction.amount()
            .toString(), PAYMENT_METHOD_PAYPAL ,transaction.getType());
  }

  public void sendPaymentEvent() {
    analytics.sendPaymentEvent(transaction.getDomain(), transaction.getSkuId(),
        transaction.amount()
            .toString(), PAYMENT_METHOD_PAYPAL, transaction.getType());
  }

  public void sendRevenueEvent() {
    inAppPurchaseInteractor.convertToFiat(transaction.amount().doubleValue(),
            EVENT_REVENUE_CURRENCY)
        .doOnSuccess(fiatValue -> analytics.sendRevenueEvent(String.valueOf(fiatValue.getAmount())))
        .subscribe();
  }
}