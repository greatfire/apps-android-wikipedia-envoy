package org.wikipedia.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wikipedia.BuildConfig
import org.wikipedia.R
import org.wikipedia.activity.BaseActivity
import org.wikipedia.compose.components.HtmlText
import org.wikipedia.compose.components.Snackbar
import org.wikipedia.compose.components.WikiTopAppBar
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.compose.theme.WikipediaTheme
import org.wikipedia.theme.Theme
import org.wikipedia.util.DeviceUtil

class AboutActivityWu : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtil.setEdgeToEdge(this)
        setContent {
            BaseTheme {
                AboutWikiUnblockedScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    versionName = BuildConfig.VERSION_NAME,
                    onBackButtonClick = {
                        onBackPressedDispatcher.onBackPressed()
                    }
                )
            }
        }
    }
}

@Composable
fun AboutWikiUnblockedScreen(
    modifier: Modifier = Modifier,
    versionName: String,
    onBackButtonClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(
                        message = it.visuals.message
                    )
                }
            )
        },
        topBar = {
            WikiTopAppBar(
                title = stringResource(R.string.about_wuactivity_title),
                onNavigationClick = onBackButtonClick
            )
        },
        containerColor = WikipediaTheme.colors.paperColor,
        content = { paddingValues ->
           AboutWuScreenContent(
               modifier = Modifier
                   .padding(paddingValues),
               versionName = versionName
           )
        }
    )
}

@Composable
fun AboutWuScreenContent(
    modifier: Modifier = Modifier,
    versionName: String
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AboutWikiUnblockedHeader(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp, bottom = 16.dp),
            versionName = versionName
        )
        SelectionContainer {
            AboutWuScreenBody(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun AboutWikiUnblockedHeader(
    modifier: Modifier = Modifier,
    versionName: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AboutWikiUnblockedImage()
        SelectionContainer {
            Text(
                modifier = Modifier
                    .padding(vertical = 16.dp),
                text = versionName,
                color = WikipediaTheme.colors.primaryColor
            )
        }
    }
}

@Composable
fun AboutWikiUnblockedImage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier.size(88.dp),
            painter = painterResource(R.drawable.feed_header_wordmark),
            contentDescription = stringResource(R.string.about_logo_content_description),
        )
    }
}

@Composable
fun AboutWuScreenBody(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        WuLinkTextWithHeader(
            header = stringResource(R.string.about_website_heading),
            html = stringResource(R.string.about_website)
        )
    }
}

@Composable
fun WuLinkTextWithHeader(
    modifier: Modifier = Modifier,
    header: String,
    html: String,
    linkStyles: TextLinkStyles = TextLinkStyles(
        style = SpanStyle(
            color = WikipediaTheme.colors.progressiveColor
        )
    )
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.bodyLarge,
            color = WikipediaTheme.colors.primaryColor
        )
        HtmlText(
            text = html,
            linkStyle = linkStyles
        )
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        AboutWikiUnblockedScreen(
            modifier = Modifier
                .fillMaxSize(),
            versionName = "version name",
            onBackButtonClick = {}
        )
    }
}
