package org.wikipedia.main

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greatfire.envoy.*
import org.greatfire.wikiunblocked.Secrets
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.activity.SingleFragmentActivity
import org.wikipedia.analytics.eventplatform.ContributionsDashboardEvent
import org.wikipedia.analytics.eventplatform.ImageRecommendationsEvent
import org.wikipedia.analytics.eventplatform.PatrollerExperienceEvent
import org.wikipedia.auth.AccountUtil
import org.wikipedia.databinding.ActivityMainBinding
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.donate.DonorStatus
import org.wikipedia.feed.FeedFragment
import org.wikipedia.navtab.NavTab
import org.wikipedia.onboarding.InitialOnboardingActivity
import org.wikipedia.page.PageActivity
import org.wikipedia.settings.Prefs
import org.wikipedia.usercontrib.ContributionsDashboardHelper
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.ResourceUtil
import org.wikipedia.views.DonorBadgeView

class MainActivity : SingleFragmentActivity<MainFragment>(), MainFragment.Callback {

    init {
        instance = this
    }

    // event logging
    /*
    private var eventHandler: EventHandler? = null
    private val EVENT_TAG_SELECT = "SELECTED_URL"
    private val EVENT_PARAM_SELECT_URL = "selected_url_value"
    private val EVENT_PARAM_SELECT_SERVICE = "selected_url_service"
    private val EVENT_TAG_VALID = "VALID_URL"
    private val EVENT_PARAM_VALID_URL = "valid_url_value"
    private val EVENT_PARAM_VALID_SERVICE = "valid_url_service"
    private val EVENT_TAG_INVALID = "INVALID_URL"
    private val EVENT_PARAM_INVALID_URL = "invalid_url_value"
    private val EVENT_PARAM_INVALID_SERVICE = "invalid_url_service"
    private val EVENT_TAG_UPDATE_SUCCEEDED = "UPDATE_SUCCEEDED"
    private val EVENT_PARAM_UPDATE_SUCCEEDED_URL = "update_succeeded_url"
    private val EVENT_PARAM_UPDATE_SUCCEEDED_COUNT = "update_succeeded_count"
    private val EVENT_TAG_UPDATE_FAILED = "UPDATE_FAILED"
    private val EVENT_PARAM_UPDATE_FAILED_URL = "update_failed_url"
    private val EVENT_TAG_CONTINUED = "VALIDATION_CONTINUED"
    private val EVENT_TAG_VALIDATION_TIME = "VALIDATION_TIME"
    private val EVENT_PARAM_VALIDATION_SECONDS = "validation_time_seconds"
    private val EVENT_TAG_VALIDATION_ENDED = "VALIDATION_ENDED"
    private val EVENT_PARAM_VALIDATION_ENDED_CAUSE = "validation_ended_cause"
    */

    private lateinit var binding: ActivityMainBinding

    private var controlNavTabInFragment = false
    private val onboardingLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val fragment = fragment.currentFragment
        if (it.resultCode == InitialOnboardingActivity.RESULT_LANGUAGE_CHANGED && fragment is FeedFragment) {
            fragment.refresh()
        }
    }

    private val mCallback = WikiCallback()

    class WikiCallback: EnvoyTestCallback {

        val mainScope = CoroutineScope(Dispatchers.Main)

        // TODO: restore analytics logging

        override fun reportTestSuccess(testedUrl: String, testedService: String, time: Long) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl, testedService)
            Log.d(TAG, "URL: $sanitizedUrl VALID! TIME: $time ms")

            // populate debug menu
            if (BuildConfig.BUILD_TYPE == "debug" && !testedService.isNullOrEmpty()) {
                validServices.add(testedService + " - " + sanitizedUrl)
                Prefs.validServices = validServices
            }

            if (EnvoyTransportType.DIRECT.name.equals(testedService)) {
                Log.d(TAG, "DIRECT CONNECTION SUCCESSFUL")
                // set flag so resuming activity doesn't trigger another envoy check
                envoyUnused = true
            } else {
                mainScope.launch {
                    if (waitingForEnvoy) {
                        Log.d(TAG, "FIRST VALID URL, REFRESH UI")
                        // when the first valid url is received, refresh ui
                        waitingForEnvoy = false

                        val fragment = mainActivityFragment()
                        if (fragment is MainFragment) {
                            Log.d(TAG, "REFRESH MAIN FRAGMENT")
                            fragment.refreshFragment()
                        } else {
                            Log.d(TAG, "UNEXPECTED FRAGMENT CLASS")
                        }
                    } else {
                        Log.d(TAG, "EXTRA VALID URL, IGNORE")
                    }
                }
            }
        }

        override fun reportTestFailure(testedUrl: String, testedService: String, time: Long) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl, testedService)
            Log.d(TAG, "URL: $sanitizedUrl INVALID! TIME: $time ms")

            // populate debug menu
            if (BuildConfig.BUILD_TYPE == "debug" && !testedService.isNullOrEmpty()) {
                invalidServices.add(testedService + " - " + sanitizedUrl)
                Prefs.invalidServices = invalidServices
            }
        }

        override fun reportTestBlocked(testedUrl: String, testedService: String) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl, testedService)
            Log.e(TAG, "URL: $sanitizedUrl BLOCKED! (RETRY LATER)")

            // populate debug menu (add to invalid list)
            if (BuildConfig.BUILD_TYPE == "debug" && !testedService.isNullOrEmpty()) {
                invalidServices.add(testedService + " - " + sanitizedUrl)
                Prefs.invalidServices = invalidServices
            }
        }

        override fun reportOverallStatus(status: String, time: Long) {
            Log.d(TAG, "FINISHED! TIME: $time ms")
            Log.d(TAG, "STATUS: $status")
            if (EnvoyTestStatus.BLOCKED.name.equals(status) && !envoyUnused) {
                // all urls blocked due to previous failures, show dialog advising to
                // retry later, but ignore if direct connection was successful
                Log.w(TAG, "envoy failed, but all urls were blocked")
                mainScope.launch {
                    showFailureDialog()
                }
            } else if (EnvoyTestStatus.TIMEOUT.name.equals(status) && !envoyUnused) {
                // setup failed but not all urls were tested, show dialog with retry
                // prompt, but ignore if direct connection was successful
                Log.w(TAG, "envoy failed, but not all urls were tested")
                mainScope.launch {
                    showRetryDialog()
                }
            } else {
                // NO-OP?
            }
        }
    }

    // TODO - remove after all relevant functionality is restored
    // this receiver should be triggered by a success or failure broadcast from the
    // NetworkIntentService (indicating whether submitted urls were valid or invalid)
    /*
    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && context != null) {
                if (intent.action == ENVOY_BROADCAST_VALIDATION_SUCCEEDED) {
                    val validUrl = intent.getStringExtra(ENVOY_DATA_URL_SUCCEEDED) ?: ""
                    var validService = intent.getStringExtra(ENVOY_DATA_SERVICE_SUCCEEDED) ?: ""
                    if (validUrl.startsWith(ENVOY_SERVICE_ENVOY) && validService.startsWith(ENVOY_SERVICE_HTTPS)) {
                        validService = ENVOY_SERVICE_ENVOY
                    }
                    val sanitizedUrl = UrlUtil.sanitizeUrl(validUrl, validService)

                    // populate debug menu
                    if (BuildConfig.BUILD_TYPE == "debug" && !validService.isNullOrEmpty()) {
                        validServices.add(validService + " - " + sanitizedUrl)
                        Prefs.validServices = validServices
                    }

                    if (validUrl.isNullOrEmpty()) {
                        Log.e(TAG, "received a valid url that was empty or null")
                    } else if (waitingForEnvoy) {
                        // select the first valid url that is received (assumed to have the lowest latency)
                        waitingForEnvoy = false

                        // record the validation time of the first valid url received
                        val validationMs = intent.getLongExtra(ENVOY_DATA_VALIDATION_MS, 0L)
                        if (validationMs <= 0L) {
                            Log.e(TAG, "received an envoy validation succeeded broadcast with an invalid duration")
                        } else {
                            var validationSeconds: Long = validationMs / 1000L

                            // round up small values
                            if (validationSeconds < 1) {
                                validationSeconds = 1
                            }

                            //val bundle = Bundle()
                            //bundle.putLong(EVENT_PARAM_VALIDATION_SECONDS, validationSeconds)
                            //eventHandler?.logEvent(EVENT_TAG_VALIDATION_TIME, bundle)
                        }

                        //val bundle = Bundle()
                        //bundle.putString(EVENT_PARAM_SELECT_URL, sanitizedUrl)
                        //bundle.putString(EVENT_PARAM_SELECT_SERVICE, validService)
                        //eventHandler?.logEvent(EVENT_TAG_SELECT, bundle)

                        if (DIRECT_URL.contains(validUrl)) {

                            Log.d(TAG, "received a direct url: " + sanitizedUrl + ", don't need to start engine")

                            // set flag so resuming activity doesn't trigger another envoy check
                            envoyUnused = true
                        } else {

                            Log.d(TAG, "received a valid url: " + sanitizedUrl + ", start engine")
                            CronetNetworking.initializeCronetEngine(context, validUrl)

                            if (fragment is MainFragment) {
                                Log.d(TAG, "engine started, refresh main fragment")
                                fragment.refreshFragment()
                            } else {
                                Log.d(TAG, "unexpected fragment class, can't refresh")
                            }
                        }
                    } else {

                        //val bundle = Bundle()
                        //bundle.putString(EVENT_PARAM_VALID_URL, sanitizedUrl)
                        //bundle.putString(EVENT_PARAM_VALID_SERVICE, validService)
                        //eventHandler?.logEvent(EVENT_TAG_VALID, bundle)

                        Log.d(TAG, "already received a valid url, ignore additional valid url: " + sanitizedUrl)
                    }
                } else if (intent.action == ENVOY_BROADCAST_VALIDATION_FAILED) {
                    val invalidUrl = intent.getStringExtra(ENVOY_DATA_URL_FAILED) ?: ""
                    var invalidService = intent.getStringExtra(ENVOY_DATA_SERVICE_FAILED) ?: ""
                    if (invalidUrl.startsWith(ENVOY_SERVICE_ENVOY) && invalidService.startsWith(ENVOY_SERVICE_HTTPS)) {
                        invalidService = ENVOY_SERVICE_ENVOY
                    }
                    val sanitizedUrl = UrlUtil.sanitizeUrl(invalidUrl, invalidService)

                    // populate debug menu
                    if (BuildConfig.BUILD_TYPE == "debug" && !invalidService.isNullOrEmpty()) {
                        invalidServices.add(invalidService + " - " + sanitizedUrl)
                        Prefs.invalidServices = invalidServices
                    }

                    if (invalidUrl.isNullOrEmpty()) {
                        Log.e(TAG, "received an invalid url that was empty or null")
                    } else {

                        //val bundle = Bundle()
                        //bundle.putString(EVENT_PARAM_INVALID_URL, sanitizedUrl)
                        //bundle.putString(EVENT_PARAM_INVALID_SERVICE, invalidService)
                        //eventHandler?.logEvent(EVENT_TAG_INVALID, bundle)

                        Log.d(TAG, "received an invalid url: " + sanitizedUrl)
                    }
                } else if (intent.action == ENVOY_BROADCAST_BATCH_SUCCEEDED) {
                    val urlBatch = intent.getStringArrayListExtra(ENVOY_DATA_URL_LIST)
                    val serviceBatch = intent.getStringArrayListExtra(ENVOY_DATA_SERVICE_LIST)
                    if (urlBatch.isNullOrEmpty() || serviceBatch.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy batch succeeded broadcast with no urls/services")
                    } else {
                        // may be redundant, direct urls should not be included in batch
                        urlBatch.removeAll(DIRECT_URL)

                        //val bundle = Bundle()
                        // parameter limit is 100 characters, arrays not allowed
                        //bundle.putString(
                        //    EVENT_PARAM_VALID_URLS,
                        //    UrlUtil.sanitizeUrlList(urlBatch, serviceBatch)
                        //)
                        //bundle.putString(
                        //    EVENT_PARAM_VALID_SERVICES,
                        //    UrlUtil.sanitizeServiceList(serviceBatch)
                        //)
                        //eventHandler?.logEvent(EVENT_TAG_VALID_BATCH, bundle)
                    }
                } else if (intent.action == ENVOY_BROADCAST_BATCH_FAILED) {
                    val urlBatch = intent.getStringArrayListExtra(ENVOY_DATA_URL_LIST)
                    val serviceBatch = intent.getStringArrayListExtra(ENVOY_DATA_SERVICE_LIST)
                    if (urlBatch.isNullOrEmpty() || serviceBatch.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy batch failed broadcast with no urls/services")
                    } else {
                        // may be redundant, direct urls should not be included in batch
                        urlBatch.removeAll(DIRECT_URL)

                        //val bundle = Bundle()
                        // parameter limit is 100 characters, arrays not allowed
                        //bundle.putString(
                        //    EVENT_PARAM_INVALID_URLS,
                        //    UrlUtil.sanitizeUrlList(urlBatch, serviceBatch)
                        //)
                        //bundle.putString(
                        //    EVENT_PARAM_INVALID_SERVICES,
                        //    UrlUtil.sanitizeServiceList(serviceBatch)
                        //)
                        //eventHandler?.logEvent(EVENT_TAG_INVALID_BATCH, bundle)
                    }
                } else if (intent.action == ENVOY_BROADCAST_UPDATE_SUCCEEDED) {
                    //val bundle = Bundle()
                    val url = intent.getStringExtra(ENVOY_DATA_UPDATE_URL)

                    val msg = intent.getStringExtra(ENVOY_DATA_UPDATE_STATUS) ?: "null successful update message"
                    Log.d(TAG, "got successful update status: " + msg)
                    var status = ""

                    if (url.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy update succeeded broadcast with no url")
                        status = status + "got update: "
                    } else {
                        val sanitizedUrl = UrlUtil.sanitizeUrl(url, ENVOY_SERVICE_UPDATE)
                        Log.d(TAG, "envoy update succeeded for url: " + sanitizedUrl)
                        //bundle.putString(EVENT_PARAM_UPDATE_SUCCEEDED_URL, sanitizedUrl)
                        status = status + "got update from " + sanitizedUrl + ": "
                    }
                    val extraUrls = intent.getStringArrayListExtra(ENVOY_DATA_UPDATE_LIST)
                    if (extraUrls.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy update succeeded broadcast with no list")
                        status = status + "no urls"
                    } else {
                        //bundle.putInt(EVENT_PARAM_UPDATE_SUCCEEDED_COUNT, extraUrls.size)
                        status = status + extraUrls.size + " urls"
                    }
                    //eventHandler?.logEvent(EVENT_TAG_UPDATE_SUCCEEDED, bundle)
                    updateMessages.add(status)
                    Prefs.updateMessages = updateMessages
                } else if (intent.action == ENVOY_BROADCAST_UPDATE_FAILED) {
                    //val bundle = Bundle()
                    val url = intent.getStringExtra(ENVOY_DATA_UPDATE_URL)

                    val msg = intent.getStringExtra(ENVOY_DATA_UPDATE_STATUS) ?: "null failed update message"
                    Log.d(TAG, "got failed update status: " + msg)
                    updateMessages.add(msg)
                    Prefs.updateMessages = updateMessages

                    if (url.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy update failed broadcast with no url")
                    } else {
                        val sanitizedUrl = UrlUtil.sanitizeUrl(url, ENVOY_SERVICE_UPDATE)
                        Log.d(TAG, "envoy update failed for url: " + sanitizedUrl)
                        //bundle.putString(EVENT_PARAM_UPDATE_FAILED_URL, sanitizedUrl)
                    }
                    //eventHandler?.logEvent(EVENT_TAG_UPDATE_FAILED, bundle)
                } else if (intent.action == ENVOY_BROADCAST_VALIDATION_CONTINUED) {
                    Log.d(TAG, "received an envoy continuation broadcast")
                    //val bundle = Bundle()
                    //eventHandler?.logEvent(EVENT_TAG_CONTINUED, bundle)
                } else if (intent.action == ENVOY_BROADCAST_VALIDATION_ENDED) {
                    Log.e(TAG, "received an envoy validation ended broadcast")

                    waitingForEnvoy = false

                    val validationMs = intent.getLongExtra(ENVOY_DATA_VALIDATION_MS, 0L)
                    if (validationMs <= 0L) {
                        Log.e(TAG, "received an envoy validation ended broadcast with an invalid duration")
                    } else {
                        var validationSeconds: Long = validationMs / 1000L

                        // round up small values
                        if (validationSeconds < 1) {
                            validationSeconds = 1
                        }

                        //val bundle = Bundle()
                        //bundle.putLong(EVENT_PARAM_VALIDATION_SECONDS, validationSeconds)
                        //eventHandler?.logEvent(EVENT_TAG_VALIDATION_TIME, bundle)
                    }

                    val cause = intent.getStringExtra(ENVOY_DATA_VALIDATION_ENDED_CAUSE)
                    if (cause.isNullOrEmpty()) {
                        Log.e(TAG, "received an envoy validation ended broadcast with an invalid cause")
                    } else {
                        //val bundle = Bundle()
                        //bundle.putString(EVENT_PARAM_VALIDATION_ENDED_CAUSE, cause)
                        //eventHandler?.logEvent(EVENT_TAG_VALIDATION_ENDED, bundle)

                        // display dialog to allow user to retry if possible
                        if (envoyUnused == true) {
                            Log.w(TAG, "envoy failed, but direct connection worked")
                        } else if (cause.equals(ENVOY_ENDED_EMPTY)) {
                            Log.w(TAG, "envoy failed, but no urls were submitted")
                        } else if (cause.equals(ENVOY_ENDED_FAILED) || cause.equals(ENVOY_ENDED_BLOCKED)) {
                            Log.w(TAG, "envoy failed, all urls failed or were previously blocked")
                            if (retryDialog != null) {
                                Log.w(TAG, "dialog already awaiting response")
                            } else {
                                retryDialog = AlertDialog.Builder(this@MainActivity)
                                    .setTitle(R.string.failure_dialog_title)
                                    .setMessage(R.string.failure_dialog_content)
                                    .setNegativeButton(R.string.failure_dialog_button_close, cancelListener)
                                    .create()
                                retryDialog?.show()
                            }
                        } else if (cause.equals(ENVOY_ENDED_TIMEOUT)) {
                            Log.w(TAG, "envoy failed, but not all urls were tested")
                            if (retryDialog != null) {
                                Log.w(TAG, "dialog already awaiting response")
                            } else {
                                retryDialog = AlertDialog.Builder(this@MainActivity)
                                    .setTitle(R.string.retry_dialog_title)
                                    .setMessage(R.string.retry_dialog_content)
                                    .setPositiveButton(R.string.retry_dialog_button_retry, retryListener)
                                    .setNegativeButton(R.string.retry_dialog_button_close, cancelListener)
                                    .create()
                                retryDialog?.show()
                            }
                        } else {
                            // ENVOY_ENDED_UNKNOWN or other cause
                            Log.w(TAG, "envoy failed, cause unclear")
                        }
                    }
                } else {
                    Log.e(TAG, "received an unexpected intent: " + intent.action)
                }
            } else {
                Log.e(TAG, "receiver triggered but context or intent was null")
            }
        }
    }
    */

    override fun inflateAndSetContentView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // reset in onCreate, check in onResume
        envoyUnused = false

        // firebase logging
        //if (Prefs.isFirebaseLoggingEnabled) {
        //    eventHandler = EventHandler(applicationContext)
        //} else {
        //    Log.d("ENVOY_LOG", "firebase logging off, don't initialize firebase")
        //    eventHandler = null
        //}

        setImageZoomHelper()
        if (Prefs.isInitialOnboardingEnabled && savedInstanceState == null &&
            !intent.hasExtra(Constants.INTENT_EXTRA_PREVIEW_SAVED_READING_LISTS)) {
            onboardingLauncher.launch(InitialOnboardingActivity.newIntent(this))
        }
        setNavigationBarColor(ResourceUtil.getThemedColor(this, R.attr.paper_color))
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.mainToolbar.navigationIcon = null

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        // start cronet here to prevent exception from starting a service when out of focus
        checkAndInitEnvoy()
    }

    fun checkAndInitEnvoy() {

        // TODO: onCreate also checks the following before onboarding, is that necessary here?
        // savedInstanceState == null && !intent.hasExtra(Constants.INTENT_EXTRA_IMPORT_READING_LISTS
        if (Prefs.isInitialOnboardingEnabled) {
            Log.d(TAG, "user is likely doing onboarding, don't try to start envoy")
            return
        } else if (waitingForEnvoy) {
            Log.d(TAG, "already processing urls, don't try to start envoy again")
            return
        } else if (envoyUnused) {
            Log.d(TAG, "direct connection previously worked, don't try to start envoy")
            return
        } else if (validServices.isNotEmpty()) {
            // TODO: is there a way to check if an envoy service is running?
            Log.d(TAG, "vaid url already found, don't try to start envoy")
            return
        } else {
            Log.d(TAG, "starting envoy")
            waitingForEnvoy = true
        }

        // clear debug ui
        if (BuildConfig.BUILD_TYPE == "debug") {
            validServices.clear()
            Prefs.validServices = validServices
            invalidServices.clear()
            Prefs.invalidServices = invalidServices
            updateMessages.clear()
            Prefs.updateMessages = updateMessages
        }
        invalidateOptionsMenu()

        // secrets don't support fdroid package name
        val shortPackage = packageName.removeSuffix(".fdroid")

        Log.d(TAG, "GET SECRETS: " + shortPackage)

        val urlString: String = Secrets().getdefProxy(shortPackage)
        val testUrls: List<String> = urlString.split(",")

        val envoy: EnvoyNetworking = EnvoyNetworking()

        envoy.setContext(mainActivityAppContext())

        // skip direct for now
        // envoy.addEnvoyUrl(WIKI_URL)
        testUrls.forEach{
            envoy.addEnvoyUrl(it)
        }

        envoy.setPassiveTest(false)
        envoy.setCallback(mCallback)
        envoy.connect()

        Log.d(TAG, "ENVOY TESTING STARTED...")
    }

    override fun createFragment(): MainFragment {
        return MainFragment.newInstance()
    }

    override fun onTabChanged(tab: NavTab) {
        if (tab == NavTab.EXPLORE) {
            binding.mainToolbarWordmark.visibility = View.VISIBLE
            binding.mainToolbar.title = ""
            binding.toolbarTitle.isVisible = false
            binding.donorBadge.isVisible = false
            controlNavTabInFragment = false
        } else {
            binding.toolbarTitle.isVisible = true
            binding.donorBadge.isVisible = false
            if (tab == NavTab.SEARCH && Prefs.showSearchTabTooltip) {
                FeedbackUtil.showTooltip(this, fragment.binding.mainNavTabLayout.findViewById(NavTab.SEARCH.id), getString(R.string.search_tab_tooltip), aboveOrBelow = true, autoDismiss = false)
                Prefs.showSearchTabTooltip = false
            }
            var titleText = getString(tab.text)
            if (tab == NavTab.EDITS) {
                ImageRecommendationsEvent.logImpression("suggested_edit_dialog")
                PatrollerExperienceEvent.logImpression("suggested_edits_dialog")
                if (ContributionsDashboardHelper.contributionsDashboardEnabled) {
                    titleText = if (AccountUtil.isLoggedIn) {
                        AccountUtil.userName
                    } else {
                        getString(R.string.contributions_dashboard_logged_out_user)
                    }
                    binding.donorBadge.disableClickForDonor()
                    binding.donorBadge.setup(object : DonorBadgeView.Callback {
                        override fun onBecomeDonorClick() {
                            ContributionsDashboardEvent.logAction("donate_start_click", "contrib_dashboard", campaignId = ContributionsDashboardHelper.CAMPAIGN_ID)
                            launchDonateDialog(campaignId = ContributionsDashboardHelper.CAMPAIGN_ID)
                        }
                    })
                    binding.donorBadge.isVisible = DonorStatus.donorStatus() != DonorStatus.UNKNOWN
                }
            }
            binding.mainToolbarWordmark.visibility = View.GONE
            binding.toolbarTitle.text = titleText
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

        private const val TAG = "MainActivity"

        private const val WIKI_URL = "https://www.wikipedia.org/";

        // state/ui parameters moved here to allow access from callback

        private var waitingForEnvoy = false
        private var envoyUnused = false

        private val validServices = mutableListOf<String>()
        private val invalidServices = mutableListOf<String>()
        private val updateMessages = mutableListOf<String>()

        private var currentDialog: AlertDialog? = null

        private var instance: MainActivity? = null

        private val retryListener: DialogInterface.OnClickListener = object : DialogInterface.OnClickListener {
            override fun onClick(p0: DialogInterface?, p1: Int) {
                Log.d(TAG, "retry envoy from dialog")
                instance!!.checkAndInitEnvoy()
                currentDialog?.cancel()
                currentDialog = null
            }
        }
        private val cancelListener: DialogInterface.OnClickListener = object : DialogInterface.OnClickListener {
            override fun onClick(p0: DialogInterface?, p1: Int) {
                Log.d(TAG, "cancel dialog")
                currentDialog?.cancel()
                currentDialog = null
            }
        }

        fun showFailureDialog() {
            if (currentDialog != null) {
                Log.w(TAG, "dialog already awaiting response")
            } else {
                currentDialog = AlertDialog.Builder(instance!!)
                    .setTitle(R.string.failure_dialog_title)
                    .setMessage(R.string.failure_dialog_content)
                    .setNegativeButton(R.string.failure_dialog_button_close, cancelListener)
                    .create()
                currentDialog?.show()
            }
        }

        fun showRetryDialog() {
            if (currentDialog != null) {
                Log.w(TAG, "dialog already awaiting response")
            } else {
                currentDialog = AlertDialog.Builder(instance!!)
                    .setTitle(R.string.retry_dialog_title)
                    .setMessage(R.string.retry_dialog_content)
                    .setPositiveButton(R.string.retry_dialog_button_retry, retryListener)
                    .setNegativeButton(R.string.retry_dialog_button_close, cancelListener)
                    .create()
                currentDialog?.show()
            }
        }

        fun mainActivityAppContext() : Context {
            return instance!!.applicationContext
        }

        fun mainActivityFragment(): Fragment {
            return instance!!.fragment
        }

        fun newIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }
}
