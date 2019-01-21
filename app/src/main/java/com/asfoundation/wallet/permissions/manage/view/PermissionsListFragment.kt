package com.asfoundation.wallet.permissions.manage.view

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.appcoins.wallet.permissions.ApplicationPermission
import com.appcoins.wallet.permissions.PermissionName
import com.asf.wallet.R
import com.asfoundation.wallet.permissions.AndroidAppDataProvider
import com.asfoundation.wallet.permissions.PermissionsInteractor
import dagger.android.support.DaggerFragment
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.item_appcoins_application_list.*
import javax.inject.Inject

class PermissionsListFragment : DaggerFragment(), PermissionsListView {
  companion object {
    fun newInstance(): Fragment {
      return PermissionsListFragment()
    }

    private val TAG = PermissionsListFragment::class.java.simpleName
  }

  @Inject
  lateinit var permissionsInteractor: PermissionsInteractor
  private lateinit var presenter: PermissionsListPresenter
  private lateinit var adapter: PermissionsListAdapter
  private lateinit var appInfoProvider: AndroidAppDataProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    presenter =
        PermissionsListPresenter(this, permissionsInteractor, AndroidSchedulers.mainThread(),
            CompositeDisposable())
    adapter = PermissionsListAdapter(mutableListOf())
    appInfoProvider = AndroidAppDataProvider(context!!)
  }

  override fun showPermissions(permissions: List<ApplicationPermission>): Completable {
    Log.d(TAG, "showPermissions() called with: permissions = [$permissions]")
    return Single.fromCallable { map(permissions) }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess { adapter.setPermissions(it) }
        .ignoreElement()
  }

  private fun map(permissions: List<ApplicationPermission>): List<ApplicationPermissionViewData> {
    return permissions.map {
      val appInfo = appInfoProvider.getAppInfo(it.packageName)
      ApplicationPermissionViewData(it.packageName, appInfo.appName.toString(),
          it.permissions.contains(PermissionName.WALLET), appInfo.icon)
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.fragment_permissions_list_layout, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    recycler_view.adapter = adapter
    recycler_view.layoutManager = LinearLayoutManager(context)
    presenter.present()
  }

  override fun onDestroyView() {
    presenter.stop()
    super.onDestroyView()
  }

}
