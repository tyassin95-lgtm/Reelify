package app.reelify

import android.app.Application
import kotlinx.coroutines.launch

class ReelifyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.library.seedIfNeeded() }
    }
}
