package app.voidnotes

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.voidnotes.ui.NotesViewModel
import app.voidnotes.ui.VoidNotesRoot
import app.voidnotes.ui.theme.VoidNotesTheme

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            VoidNotesTheme(settings.theme, settings.accent, settings.dotHeadings, settings.dotGrid, settings.font) {
                VoidNotesRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.keyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        vm.onStart()
    }

    override fun onStop() {
        super.onStop()
        vm.onStop()
    }
}
