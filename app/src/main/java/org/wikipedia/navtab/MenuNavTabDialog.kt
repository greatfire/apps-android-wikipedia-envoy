package org.wikipedia.navtab

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.greatfire.envoy.CronetNetworking
import org.wikipedia.BuildConfig
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.activity.FragmentUtil
import org.wikipedia.analytics.eventplatform.BreadCrumbLogEvent
import org.wikipedia.auth.AccountUtil
import org.wikipedia.databinding.ViewMainDrawerBinding
import org.wikipedia.page.ExtendedBottomSheetDialogFragment
import org.wikipedia.settings.Prefs
import org.wikipedia.util.DimenUtil.getDimension
import org.wikipedia.util.DimenUtil.roundedDpToPx
import org.wikipedia.util.ResourceUtil.getThemedColorStateList
import org.wikipedia.util.UriUtil.visitInExternalBrowser

class MenuNavTabDialog : ExtendedBottomSheetDialogFragment() {
    interface Callback {
        fun usernameClick()
        fun loginClick()
        fun talkClick()
        fun settingsClick()
        fun watchlistClick()
        fun contribsClick()
    }

    private var _binding: ViewMainDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ViewMainDrawerBinding.inflate(inflater, container, false)

        binding.mainDrawerAccountContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerAccountContainer)
            if (AccountUtil.isLoggedIn) {
                callback()?.usernameClick()
            } else {
                callback()?.loginClick()
            }
            dismiss()
        }

        binding.mainDrawerTalkContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerTalkContainer)
            callback()?.talkClick()
            dismiss()
        }

        binding.mainDrawerWatchlistContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerWatchlistContainer)
            callback()?.watchlistClick()
            dismiss()
        }

        binding.mainDrawerSettingsContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerSettingsContainer)
            callback()?.settingsClick()
            dismiss()
        }

        binding.mainDrawerContribsContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerContribsContainer)
            callback()?.contribsClick()
            dismiss()
        }

        binding.mainDrawerDonateContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerDonateContainer)
            visitInExternalBrowser(requireContext(),
                    Uri.parse(getString(R.string.donate_url,
                            BuildConfig.VERSION_NAME, WikipediaApp.instance.languageState.systemLanguageCode)))
            dismiss()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    override fun onStart() {
        super.onStart()
        BottomSheetBehavior.from(binding.root.parent as View).peekHeight = roundedDpToPx(getDimension(R.dimen.navTabDialogPeekHeight))
    }

    private fun updateState() {
        if (AccountUtil.isLoggedIn) {
            binding.mainDrawerAccountAvatar.setImageResource(R.drawable.ic_baseline_person_24)
            ImageViewCompat.setImageTintList(binding.mainDrawerAccountAvatar, getThemedColorStateList(requireContext(), R.attr.material_theme_secondary_color))
            binding.mainDrawerAccountName.text = AccountUtil.userName
            binding.mainDrawerAccountName.visibility = View.VISIBLE
            binding.mainDrawerLoginButton.visibility = View.GONE
            binding.mainDrawerLoginOpenExternalIcon.visibility = View.VISIBLE
            binding.mainDrawerTalkContainer.visibility = View.VISIBLE
            binding.mainDrawerWatchlistContainer.visibility = View.VISIBLE
            binding.mainDrawerContribsContainer.visibility = View.VISIBLE
        } else {
            binding.mainDrawerAccountAvatar.setImageResource(R.drawable.ic_login_24px)
            ImageViewCompat.setImageTintList(binding.mainDrawerAccountAvatar, getThemedColorStateList(requireContext(), R.attr.colorAccent))
            binding.mainDrawerAccountName.visibility = View.GONE
            binding.mainDrawerLoginButton.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            binding.mainDrawerLoginButton.text = getString(R.string.main_drawer_login)
            binding.mainDrawerLoginButton.setTextColor(getThemedColorStateList(requireContext(), R.attr.colorAccent))
            binding.mainDrawerLoginOpenExternalIcon.visibility = View.GONE
            binding.mainDrawerTalkContainer.visibility = View.GONE
            binding.mainDrawerWatchlistContainer.visibility = View.GONE
            binding.mainDrawerContribsContainer.visibility = View.GONE
        }

        // check proxy state
        if (BuildConfig.BUILD_TYPE == "debug") {
            binding.mainDrawerProxyContainer.visibility = View.GONE
            binding.mainDrawerValidContainer.visibility = View.VISIBLE
            binding.mainDrawerInvalidContainer.visibility = View.VISIBLE
            binding.mainDrawerUpdateContainer.visibility = View.VISIBLE
            var validString = Prefs.validServices.toString().removePrefix("[").removeSuffix("]").replace(", ", "\n")
            if (!validString.isNullOrEmpty()) {
                binding.mainDrawerValidText.text = validString
            }
            var invalidString = Prefs.invalidServices.toString().removePrefix("[").removeSuffix("]").replace(", ", "\n")
            if (!invalidString.isNullOrEmpty()) {
                binding.mainDrawerInvalidText.text = invalidString
            }
            var updateString = Prefs.updateMessages.toString().removePrefix("[").removeSuffix("]").replace(", ", "\n")
            if (!updateString.isNullOrEmpty()) {
                binding.mainDrawerUpdateText.text = updateString
            }
        } else {
            binding.mainDrawerProxyContainer.visibility = View.VISIBLE
            binding.mainDrawerValidContainer.visibility = View.GONE
            binding.mainDrawerInvalidContainer.visibility = View.GONE
            binding.mainDrawerUpdateContainer.visibility = View.GONE
            if (CronetNetworking.cronetEngine() == null) {
                binding.mainDrawerProxyOn.visibility = View.GONE
                binding.mainDrawerProxyOff.visibility = View.VISIBLE
            } else {
                binding.mainDrawerProxyOn.visibility = View.VISIBLE
                binding.mainDrawerProxyOff.visibility = View.GONE
            }
        }
    }

    private fun callback(): Callback? {
        return FragmentUtil.getCallback(this, Callback::class.java)
    }

    companion object {
        fun newInstance(): MenuNavTabDialog {
            return MenuNavTabDialog()
        }
    }
}
