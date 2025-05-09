package com.frontend

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log

import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader

import com.kakao.sdk.common.KakaoSdk

import java.security.MessageDigest

class MainApplication : Application(), ReactApplication {

  override val reactNativeHost: ReactNativeHost =
    object : DefaultReactNativeHost(this) {
      override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

      override fun getPackages(): List<ReactPackage> =
        PackageList(this).packages

      override fun getJSMainModuleName(): String = "index"

      override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
      override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
    }

  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()

    KakaoSdk.init(this, getString(R.string.kakao_app_key))
    logKeyHash()
    SoLoader.init(this, OpenSourceMergedSoMapping)
    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) load()
  }

  private fun logKeyHash() {
    try {
      val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        PackageManager.GET_SIGNING_CERTIFICATES
      else
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES

      val pkgInfo: PackageInfo =
        packageManager.getPackageInfo(packageName, flags)

      // Android P 이상인 경우 signingInfo, 이하인 경우 signatures
      val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.signingInfo
          ?.apkContentsSigners
          .orEmpty()
      } else {
        @Suppress("DEPRECATION")
        pkgInfo.signatures
          .orEmpty()
      }

      for (sig in sigs) {
        val md = MessageDigest.getInstance("SHA")
        md.update(sig.toByteArray())
        val keyHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
        Log.d("KeyHash", keyHash)
      }
    } catch (e: Exception) {
      Log.e("KeyHash", "Failed to get key hash", e)
    }
  }
}
