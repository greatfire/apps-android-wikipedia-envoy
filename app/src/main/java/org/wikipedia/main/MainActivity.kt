package org.wikipedia.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.greatfire.envoy.*
import org.greatfire.wikiunblocked.Secrets
import org.wikipedia.BuildConfig
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.activity.SingleFragmentActivity
import org.wikipedia.databinding.ActivityMainBinding
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.events.EventHandler
import org.wikipedia.navtab.NavTab
import org.wikipedia.onboarding.InitialOnboardingActivity
import org.wikipedia.page.PageActivity
import org.wikipedia.settings.Prefs
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.ResourceUtil

class MainActivity : SingleFragmentActivity<MainFragment>(), MainFragment.Callback {

    private val TAG = "MainActivity"

    private val DIRECT_URL = arrayListOf<String>("https://www.wikipedia.org/")

    // event logging
    private var eventHandler: EventHandler? = null
    private val EVENT_TAG_DIRECT = "DIRECT_URL"
    private val EVENT_PARAM_DIRECT_URL = "direct_url_value"
    private val EVENT_PARAM_DIRECT_SERVICE = "direct_url_service"
    private val EVENT_TAG_SELECT = "SELECTED_URL"
    private val EVENT_PARAM_SELECT_URL = "selected_url_value"
    private val EVENT_PARAM_SELECT_SERVICE = "selected_url_service"
    private val EVENT_TAG_VALID = "VALID_URL"
    private val EVENT_PARAM_VALID_URL = "valid_url_value"
    private val EVENT_PARAM_VALID_SERVICE = "valid_url_service"
    private val EVENT_TAG_INVALID = "INVALID_URL"
    private val EVENT_PARAM_INVALID_URL = "invalid_url_value"
    private val EVENT_PARAM_INVALID_SERVICE = "invalid_url_service"
    private val EVENT_TAG_VALID_BATCH = "VALID_BATCH"
    private val EVENT_PARAM_VALID_URLS = "valid_batch_urls"
    private val EVENT_PARAM_VALID_SERVICES = "valid_batch_services"
    private val EVENT_TAG_INVALID_BATCH = "INVALID_BATCH"
    private val EVENT_PARAM_INVALID_URLS = "invalid_batch_urls"
    private val EVENT_PARAM_INVALID_SERVICES = "invalid_batch_services"
    private val EVENT_TAG_UPDATE_SUCCEEDED = "UPDATE_SUCCEEDED"
    private val EVENT_PARAM_UPDATE_SUCCEEDED_URL = "update_succeeded_url"
    private val EVENT_PARAM_UPDATE_SUCCEEDED_COUNT = "update_succeeded_count"
    private val EVENT_TAG_UPDATE_FAILED = "UPDATE_FAILED"
    private val EVENT_PARAM_UPDATE_FAILED_URL = "update_failed_url"
    private val EVENT_TAG_CONTINUED = "continue_validation"

    private lateinit var binding: ActivityMainBinding

    private var controlNavTabInFragment = false
    private val onboardingLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // add all string values to this list value
    private val listOfUrls = mutableListOf<String>()
    private val invalidUrls = mutableListOf<String>()

    private var waitingForEnvoy = false
    private var envoyUnused = false

    private val validServices = mutableListOf<String>()
    private val invalidServices = mutableListOf<String>()

    // this receiver should be triggered by a success or failure broadcast from the
    // NetworkIntentService (indicating whether submitted urls were valid or invalid)
    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && context != null) {
                if (intent.action == ENVOY_BROADCAST_VALIDATION_SUCCEEDED) {
                    val validUrl = intent.getStringExtra(ENVOY_DATA_URL_SUCCEEDED)
                    val validService = intent.getStringExtra(ENVOY_DATA_SERVICE_SUCCEEDED)

                    if (BuildConfig.BUILD_TYPE == "debug" && !validService.isNullOrEmpty()) {
                        validServices.add(validService + " - " + validUrl)
                        Prefs.validServices = validServices
                    }

                    if (validUrl.isNullOrEmpty()) {
                        Log.e(TAG, "received a valid url that was empty or null")
                    } else if (waitingForEnvoy) {
                        waitingForEnvoy = false
                        // select the first url that is returned (assumed to have the lowest latency)
                        if (DIRECT_URL.contains(validUrl)) {

                            val bundle = Bundle()
                            bundle.putString(EVENT_PARAM_DIRECT_URL, validUrl)
                            bundle.putString(EVENT_PARAM_DIRECT_SERVICE, validService)
                            eventHandler?.logEvent(EVENT_TAG_DIRECT, bundle)

                            // set flag so resuming activity doesn't trigger another envoy check
                            envoyUnused = true

                            Log.d(TAG, "got direct url: " + validUrl + ", don't need to start engine")
                        } else {

                            val bundle = Bundle()
                            bundle.putString(EVENT_PARAM_SELECT_URL, validUrl)
                            bundle.putString(EVENT_PARAM_SELECT_SERVICE, validService)
                            eventHandler?.logEvent(EVENT_TAG_SELECT, bundle)

                            Log.d(TAG, "found a valid url: " + validUrl + ", start engine")
                            CronetNetworking.initializeCronetEngine(context, validUrl)

                            if (fragment is MainFragment) {
                                Log.d(TAG, "engine started, refresh main fragment")
                                fragment.refreshFragment()
                            } else {
                                Log.d(TAG, "unexpected fragment class, can't refresh")
                            }
                        }
                    } else {

                        val bundle = Bundle()
                        bundle.putString(EVENT_PARAM_VALID_URL, validUrl)
                        bundle.putString(EVENT_PARAM_VALID_SERVICE, validService)
                        eventHandler?.logEvent(EVENT_TAG_VALID, bundle)

                        Log.d(TAG, "already selected a valid url, ignore valid url: " + validUrl)
                    }
                } else if (intent.action == ENVOY_BROADCAST_VALIDATION_FAILED) {
                    val invalidUrl = intent.getStringExtra(ENVOY_DATA_URL_FAILED)
                    val invalidService = intent.getStringExtra(ENVOY_DATA_SERVICE_FAILED)

                    if (BuildConfig.BUILD_TYPE == "debug" && !invalidService.isNullOrEmpty()) {
                        invalidServices.add(invalidService + " - " + invalidUrl)
                        Prefs.invalidServices = invalidServices
                    }

                    if (invalidUrl.isNullOrEmpty()) {
                        Log.e(TAG, "received an invalid url that was empty or null")
                    } else {

                        val bundle = Bundle()
                        bundle.putString(EVENT_PARAM_INVALID_URL, invalidUrl)
                        bundle.putString(EVENT_PARAM_INVALID_SERVICE, invalidService)
                        eventHandler?.logEvent(EVENT_TAG_INVALID, bundle)

                        Log.d(TAG, "got invalid url: " + invalidUrl)
                        invalidUrls.add(invalidUrl)
                        if (waitingForEnvoy && (invalidUrls.size >= listOfUrls.size)) {
                            Log.e(TAG, "no urls left to try, cannot start envoy/cronet")
                            // clearing this flag will cause any additional urls that follow to be ignored
                            waitingForEnvoy = false
                        } else {
                            Log.e(TAG, "still trying urls, " + invalidUrls.size + " out of " + listOfUrls.size + " failed")
                        }
                    }
                } else if (intent.action == ENVOY_BROADCAST_VALIDATION_CONTINUED) {
                    Log.d(TAG, "received an envoy continuation broadcast")
                    val bundle = Bundle()
                    eventHandler?.logEvent(EVENT_TAG_CONTINUED, bundle)
                } else if (intent.action == ENVOY_BROADCAST_BATCH_SUCCEEDED) {
                    val urlBatch = intent.getStringArrayListExtra(ENVOY_DATA_BATCH_LIST)
                    val serviceBatch = intent.getStringArrayListExtra(ENVOY_DATA_SERVICE_LIST)
                    if (urlBatch.isNullOrEmpty() || serviceBatch.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy batch succeeded broadcast with no urls/services")
                    } else {
                        // TEMP - fix in next envoy release (direct urls should not be included in batch)
                        urlBatch.removeAll(DIRECT_URL)

                        val bundle = Bundle()
                        // parameter limit is 100 characters, arrays not allowed
                        bundle.putString(
                            EVENT_PARAM_VALID_URLS,
                            urlBatch.joinToString(separator = ",", transform = { it.take(30) })
                        )
                        bundle.putString(
                            EVENT_PARAM_VALID_SERVICES,
                            serviceBatch.joinToString(separator = ",")
                        )
                        eventHandler?.logEvent(EVENT_TAG_VALID_BATCH, bundle)
                    }
                } else if (intent.action == ENVOY_BROADCAST_BATCH_FAILED) {
                    val urlBatch = intent.getStringArrayListExtra(ENVOY_DATA_BATCH_LIST)
                    val serviceBatch = intent.getStringArrayListExtra(ENVOY_DATA_SERVICE_LIST)
                    if (urlBatch.isNullOrEmpty() || serviceBatch.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy batch failed broadcast with no urls/services")
                    } else {
                        // TEMP - fix in next envoy release (direct urls should not be included in batch)
                        urlBatch.removeAll(DIRECT_URL)

                        val bundle = Bundle()
                        // parameter limit is 100 characters, arrays not allowed
                        bundle.putString(
                            EVENT_PARAM_INVALID_URLS,
                            urlBatch.joinToString(separator = ",", transform = { it.take(30) })
                        )
                        bundle.putString(
                            EVENT_PARAM_INVALID_SERVICES,
                            serviceBatch.joinToString(separator = ",")
                        )
                        eventHandler?.logEvent(EVENT_TAG_INVALID_BATCH, bundle)
                    }
                } else if (intent.action == ENVOY_BROADCAST_UPDATE_SUCCEEDED) {
                    val bundle = Bundle()
                    val url = intent.getStringExtra(ENVOY_DATA_UPDATE_URL)
                    if (url.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy update succeeded broadcast with no url")
                    } else {
                        Log.d(TAG, "envoy update succeeded for url: " + url)
                        bundle.putString(EVENT_PARAM_UPDATE_SUCCEEDED_URL, url)
                    }
                    val extraUrls = intent.getStringArrayListExtra(ENVOY_DATA_UPDATE_LIST)
                    if (extraUrls.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy update succeeded broadcast with no list")
                    } else {
                        // additional urls should be received before validation, so checking the count should work correctly
                        extraUrls.forEach { url ->
                            if (listOfUrls.contains(url)) {
                                Log.d(TAG, "already validated additional url: " + url)
                            } else {
                                Log.d(TAG, "got additional url for validation: " + url)
                                listOfUrls.add(url)
                            }
                        }
                        bundle.putInt(EVENT_PARAM_UPDATE_SUCCEEDED_COUNT, extraUrls.size)
                    }
                    eventHandler?.logEvent(EVENT_TAG_UPDATE_SUCCEEDED, bundle)
                } else if (intent.action == ENVOY_BROADCAST_UPDATE_FAILED) {
                    val bundle = Bundle()
                    val url = intent.getStringExtra(ENVOY_DATA_UPDATE_URL)
                    if (url.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy update failed broadcast with no url")
                    } else {
                        Log.d(TAG, "envoy update failed for url: " + url)
                        bundle.putString(EVENT_PARAM_UPDATE_FAILED_URL, url)
                    }
                    eventHandler?.logEvent(EVENT_TAG_UPDATE_FAILED, bundle)
                } else {
                    Log.e(TAG, "received unexpected intent: " + intent.action)
                }
            } else {
                Log.e(TAG, "receiver triggered but context or intent was null")
            }
        }
    }

    override fun inflateAndSetContentView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    fun envoyInit() {

        listOfUrls.clear()
        invalidUrls.clear()

        if (BuildConfig.BUILD_TYPE == "debug") {
            validServices.clear()
            Prefs.validServices = validServices
            invalidServices.clear()
            Prefs.invalidServices = invalidServices
        }

        // secrets don't support fdroid package name
        val shortPackage = packageName.removeSuffix(".fdroid")

        val urlSources = mutableListOf<String>()
        if (Secrets().geturlSources(shortPackage).isNullOrEmpty()) {
            Log.w(TAG, "no url sources have been provided")
        } else {
            Log.d(TAG, "found url sources: " + Secrets().geturlSources(shortPackage))
            urlSources.addAll(Secrets().geturlSources(shortPackage).split(","))
        }

        var urlInterval = 1
        if (Secrets().geturlInterval(shortPackage).isNullOrEmpty()) {
            Log.w(TAG, "no url interval has been provided")
        } else {
            Log.d(TAG, "found url interval: " + Secrets().geturlInterval(shortPackage))
            urlInterval = Secrets().geturlInterval(shortPackage).toInt()
        }

        var urlStart = 1
        if (Secrets().geturlStart(shortPackage).isNullOrEmpty()) {
            Log.w(TAG, "no url starting index has been provided")
        } else {
            Log.d(TAG, "found url starting index: " + Secrets().geturlStart(shortPackage))
            urlStart = Secrets().geturlStart(shortPackage).toInt()
        }

        var urlEnd = 1
        if (Secrets().geturlEnd(shortPackage).isNullOrEmpty()) {
            Log.w(TAG, "no url ending index has been provided")
        } else {
            Log.d(TAG, "found url ending index: " + Secrets().geturlEnd(shortPackage))
            urlEnd = Secrets().geturlEnd(shortPackage).toInt()
        }

        if (Secrets().getdefProxy(shortPackage).isNullOrEmpty()) {
            if (urlSources.isNullOrEmpty()) {
                Log.w(TAG, "no default proxy urls found and no url sources found, cannot proceed")
                return
            } else {
                Log.w(TAG, "no default proxy urls found, submit empty list to check sources for urls")
            }
        } else {
            Log.d(TAG, "found default proxy urls: " + Secrets().getdefProxy(shortPackage))
            listOfUrls.addAll(Secrets().getdefProxy(shortPackage).split(","))
        }

        NetworkIntentService.submit(
            this@MainActivity,
            listOfUrls,
            DIRECT_URL,
            Secrets().gethystCert(shortPackage),
            urlSources,
            urlInterval,
            urlStart,
            urlEnd
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // reset in onCreate, check in onResume
        envoyUnused = false

        // firebase logging
        if (Prefs.isFirebaseLoggingEnabled) {
            eventHandler = EventHandler(applicationContext)
        } else {
            Log.d("ENVOY_LOG", "firebase logging off, don't initialize firebase")
            eventHandler = null
        }

        setImageZoomHelper()
        if (Prefs.isInitialOnboardingEnabled && savedInstanceState == null && !intent.hasExtra(Constants.INTENT_EXTRA_IMPORT_READING_LISTS)) {
            // Updating preference so the search multilingual tooltip
            // is not shown again for first time users
            Prefs.isMultilingualSearchTooltipShown = false

            // Use startActivityForResult to avoid preload the Feed contents before finishing the initial onboarding.
            onboardingLauncher.launch(InitialOnboardingActivity.newIntent(this))
        }
        setNavigationBarColor(ResourceUtil.getThemedColor(this, R.attr.nav_tab_background_color))
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.mainToolbar.navigationIcon = null

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onStart() {
        super.onStart()

        // moved to start/stop to avoid an issue with registering multiple instances of the receiver when app is swiped away
        Log.d(TAG, "start/register broadcast receiver")
        // register to receive test results
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, IntentFilter().apply {
            addAction(ENVOY_BROADCAST_VALIDATION_SUCCEEDED)
            addAction(ENVOY_BROADCAST_VALIDATION_CONTINUED)
            addAction(ENVOY_BROADCAST_VALIDATION_FAILED)
            addAction(ENVOY_BROADCAST_BATCH_SUCCEEDED)
            addAction(ENVOY_BROADCAST_BATCH_FAILED)
            addAction(ENVOY_BROADCAST_UPDATE_SUCCEEDED)
            addAction(ENVOY_BROADCAST_UPDATE_FAILED)
        })
    }

    override fun onResume() {
        super.onResume()

        // start cronet here to prevent exception from starting a service when out of focus
        if (Prefs.isInitialOnboardingEnabled) {
            // TODO: onCreate also checks the following before onboarding, is that necessary here?
            // savedInstanceState == null && !intent.hasExtra(Constants.INTENT_EXTRA_IMPORT_READING_LISTS
            Log.d(TAG, "user is likely doing onboarding, don't try to start envoy")
        } else if (envoyUnused) {
            Log.d(TAG, "direct connection previously worked, don't try to start envoy")
        } else if (CronetNetworking.cronetEngine() != null) {
            Log.d(TAG, "cronet already running, don't try to start envoy again")
        } else if (waitingForEnvoy) {
            Log.d(TAG, "already processing urls, don't try to start envoy again")
        } else {
            // run envoy setup (fetches and validate urls)
            Log.d(TAG, "start envoy to process urls")
            waitingForEnvoy = true
            envoyInit()
        }

        invalidateOptionsMenu()
    }

    override fun onStop() {
        super.onStop()

        // moved to start/stop to avoid an issue with registering multiple instances of the receiver when app is swiped away
        Log.d(TAG, "stop/unregister broadcast receiver")
        // unregister receiver for test results
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
    }

    override fun createFragment(): MainFragment {
        return MainFragment.newInstance()
    }

    override fun onTabChanged(tab: NavTab) {
        binding.mainToolbar.setTitle(tab.text())
        if (tab == NavTab.EXPLORE) {
            controlNavTabInFragment = false
        } else {
            if (tab == NavTab.SEARCH && Prefs.showSearchTabTooltip) {
                FeedbackUtil.showTooltip(this, fragment.binding.mainNavTabLayout.findViewById(NavTab.SEARCH.id()), getString(R.string.search_tab_tooltip), aboveOrBelow = true, autoDismiss = false)
                Prefs.showSearchTabTooltip = false
            }
            controlNavTabInFragment = true
        }
        fragment.requestUpdateToolbarElevation()
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        if (!controlNavTabInFragment) {
            fragment.setBottomNavVisible(false)
        }
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        fragment.setBottomNavVisible(true)
    }

    override fun updateToolbarElevation(elevate: Boolean) {
        if (elevate) {
            setToolbarElevationDefault()
        } else {
            clearToolbarElevation()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fragment.handleIntent(intent)
    }

    override fun onGoOffline() {
        fragment.onGoOffline()
    }

    override fun onGoOnline() {
        fragment.onGoOnline()
    }

    override fun onBackPressed() {
        if (fragment.onBackPressed()) {
            return
        }
        super.onBackPressed()
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action && intent.data != null) {
            // TODO: handle special cases of non-article content, e.g. shared reading lists.
            intent.data?.let {
                if (it.authority.orEmpty().endsWith(WikiSite.BASE_DOMAIN)) {
                    // Pass it right along to PageActivity
                    val uri = Uri.parse(it.toString().replace("wikipedia://", WikiSite.DEFAULT_SCHEME + "://"))
                    startActivity(Intent(this, PageActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(uri))
                }
            }
        }
    }

    fun isCurrentFragmentSelected(f: Fragment): Boolean {
        return fragment.currentFragment === f
    }

    fun getToolbar(): Toolbar {
        return binding.mainToolbar
    }

    override fun onUnreadNotification() {
        fragment.updateNotificationDot(true)
    }

    private fun setToolbarElevationDefault() {
        binding.mainToolbar.elevation = DimenUtil.dpToPx(DimenUtil.getDimension(R.dimen.toolbar_default_elevation))
    }

    private fun clearToolbarElevation() {
        binding.mainToolbar.elevation = 0f
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }
}
