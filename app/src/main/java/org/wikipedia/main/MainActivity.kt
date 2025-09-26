package org.wikipedia.main

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greatfire.envoy.*
import org.greatfire.wikiunblocked.Secrets
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.activity.SingleFragmentActivity
import org.wikipedia.analytics.eventplatform.ImageRecommendationsEvent
import org.wikipedia.analytics.eventplatform.PatrollerExperienceEvent
import org.wikipedia.databinding.ActivityMainBinding
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.FeedFragment
import org.wikipedia.navtab.NavTab
import org.wikipedia.onboarding.InitialOnboardingActivity
import org.wikipedia.page.PageActivity
import org.wikipedia.settings.Prefs
import org.wikipedia.util.DeviceUtil
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.ResourceUtil

class MainActivity : SingleFragmentActivity<MainFragment>(), MainFragment.Callback {

    init {
        instance = this
    }

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

        // TODO: implement analytics to replace logging in callback methods

        val mainScope = CoroutineScope(Dispatchers.Main)

        override fun reportTestStarted(testedUrl: String, testedService: String) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl)
            Log.d(TAG, "START TEST FOR URL: $sanitizedUrl")
        }

        override fun reportTestSuccess(testedUrl: String, testedService: String, time: Long) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl)
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
                        // when the first valid url is received, refresh ui
                        waitingForEnvoy = false

                        val fragment = mainActivityFragment()
                        if (fragment is MainFragment) {
                            Log.d(TAG, "FIRST VALID URL, REFRESH UI")
                            fragment.refreshFragment()
                        } else {
                            Log.w(TAG, "UNEXPECTED FRAGMENT, CAN'T REFRESH")
                        }
                    } else {
                        Log.d(TAG, "ADDITIONAL VALID URL, IGNORE")
                    }
                }
            }
        }

        override fun reportTestFailure(testedUrl: String, testedService: String, time: Long) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl)
            Log.d(TAG, "URL: $sanitizedUrl INVALID! TIME: $time ms")

            // populate debug menu
            if (BuildConfig.BUILD_TYPE == "debug" && !testedService.isNullOrEmpty()) {
                invalidServices.add(testedService + " - " + sanitizedUrl)
                Prefs.invalidServices = invalidServices
            }
        }

        override fun reportTestBlocked(testedUrl: String, testedService: String) {
            val sanitizedUrl = UrlUtil.sanitizeUrl(testedUrl)
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
                mainScope.launch {
                    showFailureDialog()
                }
            } else if (EnvoyTestStatus.TIMEOUT.name.equals(status) && !envoyUnused) {
                // setup failed but not all urls were tested, show dialog with retry
                // prompt, but ignore if direct connection was successful
                mainScope.launch {
                    showRetryDialog()
                }
            } else {
                // NO-OP?
            }
        }
    }

    override fun inflateAndSetContentView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceUtil.assertAppContext(this)) {
            return
        }

        onBackPressedDispatcher.addCallback(this) {
            if (fragment.onBackPressed()) {
                return@addCallback
            }
            finish()
        }

        // reset in onCreate, check in onResume
        envoyUnused = false

        // TODO: firebase logging was initialized here

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

        // disable passive testing by setting static flag
        EnvoyNetworking.passivelyTestDirect = false
        val envoy: EnvoyNetworking = EnvoyNetworking()

        envoy.setContext(mainActivityAppContext())

        // comment out to skip direct testing
        envoy.addEnvoyUrl(WIKI_URL)
        testUrls.forEach{
            envoy.addEnvoyUrl(it)
        }

        envoy.setCallback(mCallback)
        envoy.connect()
    }

    override fun createFragment(): MainFragment {
        return MainFragment.newInstance()
    }

    override fun onTabChanged(tab: NavTab) {
        if (tab == NavTab.EXPLORE) {
            binding.mainToolbarWordmark.visibility = View.VISIBLE
            binding.mainToolbar.title = ""
            controlNavTabInFragment = false
        } else {
            if (tab == NavTab.SEARCH && Prefs.showSearchTabTooltip) {
                FeedbackUtil.showTooltip(this, fragment.binding.mainNavTabLayout.findViewById(NavTab.SEARCH.id), getString(R.string.search_tab_tooltip), aboveOrBelow = true, autoDismiss = false)
                Prefs.showSearchTabTooltip = false
            }
            if (tab == NavTab.EDITS) {
                ImageRecommendationsEvent.logImpression("suggested_edit_dialog")
                PatrollerExperienceEvent.logImpression("suggested_edits_dialog")
            }
            binding.mainToolbarWordmark.visibility = View.GONE
            binding.mainToolbar.setTitle(tab.text)
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
