@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import com.ebooksplayer.shelf.data.db.entity.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

private const val READER_FRAGMENT_TAG = "epub_navigator"
private const val DECORATION_GROUP_HIGHLIGHTS = "highlights"

private const val ACTION_ID_COPY = 1
private const val ACTION_ID_HIGHLIGHT = 2
private const val ACTION_ID_NOTE = 3
private const val ACTION_ID_DICTIONARY = 4
private const val ACTION_ID_SEARCH_WEB = 5

/**
 * Bridges Readium's Fragment-based EpubNavigatorFragment into Compose.
 * This is the one genuinely delicate part of the reader: Readium only
 * exposes a View/Fragment API for rendering (no Compose-native navigator
 * yet), so we host it in a FragmentContainerView and drive it imperatively.
 *
 * The MainActivity must be a FragmentActivity for this to have a
 * FragmentManager to attach to.
 */
@Composable
fun EpubNavigatorHost(
    modifier: Modifier,
    publication: Publication,
    initialLocator: Locator?,
    preferences: EpubPreferences,
    highlights: List<Highlight>,
    onLocatorChanged: (Locator) -> Unit,
    onHighlight: (Locator) -> Unit,
    onNote: (Locator) -> Unit,
    onDictionary: (String) -> Unit,
    onSearchWeb: (String) -> Unit,
    onHighlightTapped: (Highlight) -> Unit,
    onNavigatorReady: (EpubNavigatorFragment) -> Unit = {},
) {
    val activity = LocalContext.current as FragmentActivity
    val fragmentManager = activity.supportFragmentManager
    val containerId = remember { View.generateViewId() }
    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val currentOnHighlight by rememberUpdatedState(onHighlight)
    val currentOnNote by rememberUpdatedState(onNote)
    val currentOnDictionary by rememberUpdatedState(onDictionary)
    val currentOnSearchWeb by rememberUpdatedState(onSearchWeb)
    val currentOnHighlightTapped by rememberUpdatedState(onHighlightTapped)
    val currentHighlights by rememberUpdatedState(highlights)

    AndroidView(
        modifier = modifier,
        factory = { context -> FragmentContainerView(context).apply { id = containerId } },
    )

    DisposableEffect(publication) {
        var fragmentRef: EpubNavigatorFragment? = null

        val selectionActionModeCallback = buildSelectionActionModeCallback(
            context = activity,
            coroutineScope = coroutineScope,
            getFragment = { fragmentRef },
            onHighlight = { currentOnHighlight(it) },
            onNote = { currentOnNote(it) },
            onDictionary = { currentOnDictionary(it) },
            onSearchWeb = { currentOnSearchWeb(it) },
        )

        val factory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = preferences,
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                    }
                }
            },
            configuration = EpubNavigatorFragment.Configuration(
                selectionActionModeCallback = selectionActionModeCallback,
            ),
        )
        fragmentManager.fragmentFactory = factory

        val fragment = (fragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as? EpubNavigatorFragment)
            ?: run {
                fragmentManager.commitNow {
                    setReorderingAllowed(true)
                    add(containerId, EpubNavigatorFragment::class.java, android.os.Bundle(), READER_FRAGMENT_TAG)
                }
                fragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as EpubNavigatorFragment
            }
        fragmentRef = fragment
        navigatorFragment = fragment
        onNavigatorReady(fragment)

        val decorationListener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val highlight = currentHighlights.find { it.id.toString() == event.decoration.id }
                    ?: return false
                currentOnHighlightTapped(highlight)
                return true
            }
        }
        fragment.addDecorationListener(DECORATION_GROUP_HIGHLIGHTS, decorationListener)

        onDispose {
            fragment.removeDecorationListener(decorationListener)
            if (!activity.isFinishing && !fragmentManager.isDestroyed) {
                fragmentManager.findFragmentByTag(READER_FRAGMENT_TAG)?.let { existing ->
                    runCatching { fragmentManager.commitNow { remove(existing) } }
                }
            }
        }
    }

    LaunchedEffect(navigatorFragment, preferences) {
        navigatorFragment?.submitPreferences(preferences)
    }

    LaunchedEffect(navigatorFragment, highlights) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        val decorations = highlights.mapNotNull { highlight ->
            val locator = runCatching { Locator.fromJSON(JSONObject(highlight.locatorJson)) }.getOrNull()
                ?: return@mapNotNull null
            Decoration(
                id = highlight.id.toString(),
                locator = locator,
                style = Decoration.Style.Highlight(tint = highlight.colorArgb),
            )
        }
        fragment.applyDecorations(decorations, DECORATION_GROUP_HIGHLIGHTS)
    }

    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        fragment.currentLocator
            .drop(1)
            .debounce(1500)
            .collect { locator -> onLocatorChanged(locator) }
    }
}

/**
 * Custom selection toolbar (Copy/Highlight/Note/Dictionary/Search Web).
 * Providing this callback means owning the whole menu, so Copy is added
 * back explicitly - selecting text shouldn't lose a capability users
 * already have.
 */
private fun buildSelectionActionModeCallback(
    context: Context,
    coroutineScope: CoroutineScope,
    getFragment: () -> EpubNavigatorFragment?,
    onHighlight: (Locator) -> Unit,
    onNote: (Locator) -> Unit,
    onDictionary: (String) -> Unit,
    onSearchWeb: (String) -> Unit,
): ActionMode.Callback = object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu ?: return false
        menu.add(0, ACTION_ID_COPY, 0, "Copy")
        menu.add(0, ACTION_ID_HIGHLIGHT, 1, "Highlight")
        menu.add(0, ACTION_ID_NOTE, 2, "Note")
        menu.add(0, ACTION_ID_DICTIONARY, 3, "Dictionary")
        menu.add(0, ACTION_ID_SEARCH_WEB, 4, "Search Web")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val fragment = getFragment() ?: return false
        val itemId = item?.itemId ?: return false

        coroutineScope.launch {
            val selection = fragment.currentSelection() ?: return@launch
            val text = selection.locator.text.highlight.orEmpty()

            when (itemId) {
                ACTION_ID_COPY -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Selected text", text))
                }
                ACTION_ID_HIGHLIGHT -> onHighlight(selection.locator)
                ACTION_ID_NOTE -> onNote(selection.locator)
                ACTION_ID_DICTIONARY -> onDictionary(text)
                ACTION_ID_SEARCH_WEB -> onSearchWeb(text)
            }

            mode?.finish()
            fragment.clearSelection()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) = Unit
}
