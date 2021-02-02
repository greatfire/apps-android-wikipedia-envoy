package org.wikipedia.util

import android.content.res.Configuration
import android.content.res.Resources
import android.util.SparseArray
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import org.wikipedia.WikipediaApp
import org.wikipedia.language.AppLanguageLookUpTable
import org.wikipedia.page.PageTitle
import java.util.*

object L10nUtil {

    private val RTL_LANGS = arrayOf(
            "ar", "arc", "arz", "azb", "bcc", "bqi", "ckb", "dv", "fa", "glk", "he",
            "khw", "ks", "lrc", "mzn", "nqo", "pnb", "ps", "sd", "ug", "ur", "yi"
    )

    @JvmStatic
    fun isLangRTL(lang: String): Boolean {
        return Arrays.binarySearch(RTL_LANGS, lang, null) >= 0
    }

    @JvmStatic
    fun setConditionalTextDirection(view: View, lang: String) {
        view.textDirection = if (isLangRTL(lang)) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
    }

    @JvmStatic
    fun setConditionalLayoutDirection(view: View, lang: String) {
        view.layoutDirection = if (isLangRTL(lang)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    @JvmStatic
    val isDeviceRTL: Boolean
        get() = isCharRTL(Locale.getDefault().displayName[0])

    private fun isCharRTL(c: Char): Boolean {
        val dir = Character.getDirectionality(c).toInt()
        return dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt() || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt()
    }

    @JvmStatic
    fun getStringForArticleLanguage(languageCode: String, resId: Int): String {
        return getStringsForLocale(Locale(languageCode), intArrayOf(resId))[resId]
    }

    @JvmStatic
    fun getStringForArticleLanguage(title: PageTitle, resId: Int): String {
        return getStringsForLocale(Locale(title.wikiSite.languageCode()), intArrayOf(resId))[resId]
    }

    fun getStringsForArticleLanguage(title: PageTitle, resId: IntArray): SparseArray<String> {
        return getStringsForLocale(Locale(title.wikiSite.languageCode()), resId)
    }

    private fun getStringsForLocale(targetLocale: Locale,
                                    @StringRes strings: IntArray): SparseArray<String> {
        val config = currentConfiguration
        val systemLocale = ConfigurationCompat.getLocales(config)[0]
        if (systemLocale.language == targetLocale.language) {
            val localizedStrings = SparseArray<String>()
            for (stringRes in strings) {
                localizedStrings.put(stringRes, WikipediaApp.getInstance().getString(stringRes))
            }
            return localizedStrings
        }
        setDesiredLocale(config, targetLocale)
        val localizedStrings = getTargetStrings(strings, config)
        config.setLocale(systemLocale)
        resetConfiguration(config)
        return localizedStrings
    }

    private val currentConfiguration: Configuration
        get() = Configuration(WikipediaApp.getInstance().resources.configuration)

    private fun getTargetStrings(@StringRes strings: IntArray, altConfig: Configuration): SparseArray<String> {
        val localizedStrings = SparseArray<String>()
        val targetResources = Resources(WikipediaApp.getInstance().resources.assets,
                WikipediaApp.getInstance().resources.displayMetrics,
                altConfig)
        for (stringRes in strings) {
            localizedStrings.put(stringRes, targetResources.getString(stringRes))
        }
        return localizedStrings
    }

    private fun resetConfiguration(defaultConfig: Configuration) {
        Resources(WikipediaApp.getInstance().resources.assets,
                WikipediaApp.getInstance().resources.displayMetrics,
                defaultConfig)
    }

    private fun getDesiredLocale(desiredLocale: Locale): Locale {
        // TODO: maybe other language variants also have this issue, we need to add manually. e.g. kk?
        return when (desiredLocale.language) {
            AppLanguageLookUpTable.TRADITIONAL_CHINESE_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_TW_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_HK_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_MO_LANGUAGE_CODE -> Locale.TRADITIONAL_CHINESE
            AppLanguageLookUpTable.SIMPLIFIED_CHINESE_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_CN_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_SG_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_MY_LANGUAGE_CODE -> Locale.SIMPLIFIED_CHINESE
            else -> desiredLocale
        }
    }

    @JvmStatic
    fun getDesiredLanguageCode(langCode: String): String {
        return when (langCode) {
            AppLanguageLookUpTable.TRADITIONAL_CHINESE_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_TW_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_HK_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_MO_LANGUAGE_CODE -> AppLanguageLookUpTable.TRADITIONAL_CHINESE_LANGUAGE_CODE
            AppLanguageLookUpTable.SIMPLIFIED_CHINESE_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_CN_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_SG_LANGUAGE_CODE, AppLanguageLookUpTable.CHINESE_MY_LANGUAGE_CODE -> AppLanguageLookUpTable.SIMPLIFIED_CHINESE_LANGUAGE_CODE
            else -> langCode
        }
    }

    @JvmStatic
    fun setDesiredLocale(config: Configuration, desiredLocale: Locale) {
        // when loads API in chinese variant, we can get zh-hant, zh-hans and zh
        // but if we want to display chinese correctly based on the article itself, we have to
        // detect the variant from the API responses; otherwise, we will only get english texts.
        // And this might only happen in Chinese variant
        if (desiredLocale.language == AppLanguageLookUpTable.CHINESE_LANGUAGE_CODE) {
            // create a new Locale object to manage only "zh" language code based on its app language
            // code. e.g.: search "HK" article in "zh-hant" or "zh-hans" will get "zh" language code
            config.setLocale(getDesiredLocale(Locale(WikipediaApp.getInstance().language().appLanguageCode)))
        } else {
            config.setLocale(getDesiredLocale(desiredLocale))
        }
    }
}
