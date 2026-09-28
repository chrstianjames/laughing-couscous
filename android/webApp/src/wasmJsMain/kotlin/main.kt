import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.shortly.app.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Coil has no service loader on Wasm: register the Ktor network fetcher explicitly.
    SingletonImageLoader.setSafe { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    ComposeViewport(document.body!!) {
        App()
    }
}
