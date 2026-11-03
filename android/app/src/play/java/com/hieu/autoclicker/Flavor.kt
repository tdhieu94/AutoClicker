package com.hieu.autoclicker

import android.app.Activity
import android.os.SystemClock
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Bản CH Play: quảng cáo AdMob, KHÔNG tự cập nhật (CH Play tự lo việc cập nhật).
 *
 * Chính sách: quảng cáo chỉ hiện trong màn hình của app (banner dưới màn hình chính và quảng cáo
 * toàn màn hình khi bấm "Hiện bảng điều khiển"). Tuyệt đối không đặt quảng cáo trong bảng nổi
 * đè lên ứng dụng khác.
 */
object Flavor {
    const val HAS_UPDATER = false

    /** Khoảng cách tối thiểu giữa hai lần hiện quảng cáo toàn màn hình. */
    private const val INTERSTITIAL_GAP_MS = 5 * 60_000L

    private var mobileAdsStarted = false
    private var interstitial: InterstitialAd? = null
    private var lastInterstitialAt = 0L

    fun onCreate(activity: Activity, adContainer: FrameLayout, firstLaunch: Boolean) {
        // Hỏi đồng ý quảng cáo cá nhân hoá (bắt buộc với người dùng EU/UK), rồi mới tải quảng cáo.
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) startAds(activity, adContainer)
                }
            },
            { if (consent.canRequestAds()) startAds(activity, adContainer) },
        )
        // Đã đồng ý từ lần trước: tải luôn, không chờ.
        if (consent.canRequestAds()) startAds(activity, adContainer)
    }

    fun checkForUpdates(activity: Activity) = Unit

    fun beforeShowOverlay(activity: Activity, proceed: () -> Unit) {
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        if (ad == null || (lastInterstitialAt != 0L && now - lastInterstitialAt < INTERSTITIAL_GAP_MS)) {
            proceed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial(activity)
                proceed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                proceed()
            }
        }
        lastInterstitialAt = now
        ad.show(activity)
    }

    private fun startAds(activity: Activity, adContainer: FrameLayout) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!mobileAdsStarted) {
            mobileAdsStarted = true
            MobileAds.initialize(activity.applicationContext) {}
        }
        if (adContainer.childCount == 0) {
            val dm = activity.resources.displayMetrics
            val widthDp = (dm.widthPixels / dm.density).toInt()
            val banner = AdView(activity).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
            }
            adContainer.addView(banner)
            banner.loadAd(AdRequest.Builder().build())
        }
        if (interstitial == null) loadInterstitial(activity)
    }

    private fun loadInterstitial(activity: Activity) {
        InterstitialAd.load(
            activity.applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            },
        )
    }
}
