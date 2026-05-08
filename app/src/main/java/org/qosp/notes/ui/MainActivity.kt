package org.qosp.notes.ui

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkBuilder
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.components.backup.BackupService
import org.qosp.notes.components.security.NoteQuickSwitchManager
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.data.sync.fs.StorageConfig
import org.qosp.notes.data.sync.fs.toFriendlyString
import org.qosp.notes.data.sync.nextcloud.NextcloudConfig
import org.qosp.notes.databinding.ActivityMainBinding
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortNavdrawerNotebooksMethod
import org.qosp.notes.ui.editor.EditorFragment
import org.qosp.notes.ui.attachments.fromUri
import org.qosp.notes.ui.main.MainFragment
import org.qosp.notes.ui.utils.closeAndThen
import org.qosp.notes.ui.utils.collect
import org.qosp.notes.ui.utils.hideKeyboard
import org.qosp.notes.ui.utils.navigateSafely
import org.qosp.notes.ui.widget.WidgetUpdateHelper

class MainActivity : BaseActivity() {

    lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var navController: NavController

    private lateinit var binding: ActivityMainBinding
    private val activityModel: ActivityViewModel by viewModel()
    private val backendProvider by inject<BackendProvider>()
    private val noteQuickSwitchManager by inject<NoteQuickSwitchManager>()
    private lateinit var backCallback: OnBackPressedCallback
    private var pendingIntentAfterUnlock: Intent? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var isUnlockPromptShowing = false
    private var drawerReturnDestinationId: Int? = null
    private var isHiddenDoorArmed = false
    private var hiddenDoorInput = ""
    private var hiddenDoorWebsiteJob: Job? = null
    private var hiddenDoorExpiryJob: Job? = null
    private var privacyPageEntryCode = ""
    private var pendingPrivacyReturnToHome = false
    private var privacyReturnJob: Job? = null
    private var privacyCoverHideJob: Job? = null
    private var privacyReturnCover: View? = null
    private var isScreenOffReceiverRegistered = false
    private var isSecureWindowEnabled = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                preparePrivacyReturnToHome()
            }
        }
    }

    private val topLevelMenu get() = binding.navigationView.menu
    private val notebooksMenu get() = topLevelMenu.findItem(R.id.menu_notebooks).subMenu
    private val hiddenDoorSequenceItemIds = listOf(
        R.id.fragment_main,
        R.id.fragment_archive,
        R.id.fragment_deleted,
        R.id.fragment_manage_notebooks,
        R.id.fragment_tags,
        R.id.fragment_settings,
        R.id.fragment_about,
    )

    private val primaryDestinations = setOf(
        R.id.fragment_main,
        R.id.fragment_privacy,
        R.id.fragment_archive,
        R.id.fragment_deleted,
        R.id.fragment_notebook
    )
    private val secondaryDestinations = setOf(
        R.id.fragment_about,
        R.id.fragment_editor,
        R.id.fragment_manage_notebooks,
        R.id.fragment_search,
        R.id.fragment_sync_settings,
        R.id.fragment_settings,
        R.id.fragment_tags,
    )
    private val drawerPersistentDestinations = setOf(
        R.id.fragment_about,
        R.id.fragment_settings,
    )
    private val drawerHiddenDestinations = drawerPersistentDestinations + R.id.fragment_editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPrivacyReturnToHome = savedInstanceState?.getBoolean(STATE_PENDING_PRIVACY_RETURN_HOME) ?: false

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackNavigation()
        setupNavigation()

        // androidx.fragment:1.3.3 caused the FragmentContainerView to apply padding to itself when
        // the attribute fitsSystemWindows is enabled. We override it here and let the fragments decide their padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { view, insets ->
            insets
        }

        // Apply insets to the NavigationView to prevent it from overlapping with the status bar
        // This is to fix the insets after Android 15 enforcing edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(binding.navigationView) { view, insets ->
            // Reduce padding but keep it below the status bar
            view.setPadding(0, 0, 0, 0)
            insets
        }

        setupDrawerHeader()
        observePrivacyPageEntryCode()
        observePrivacyWindowSecurity()
        registerScreenOffReceiver()

        WidgetUpdateHelper.updateAllWidgets(this)
        if (intent != null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        privacyReturnJob?.cancel()
        applyPendingPrivacyReturnToHome()
    }

    override fun onDestroy() {
        unregisterScreenOffReceiver()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (navController.currentDestination?.id in drawerPersistentDestinations &&
            drawerReturnDestinationId != null
        ) {
            returnToDrawerDestinationKeepingOpen(drawerReturnDestinationId ?: R.id.fragment_main)
            return true
        }
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.repeatCount == 0 &&
                (
                    navController.currentDestination?.id == R.id.fragment_privacy ||
                        (navController.currentDestination?.id == R.id.fragment_main &&
                            activityModel.isPrivacyPageActive.value)
                    )
            ) {
                returnToMainFromPrivacyPage()
                return true
            }

            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.repeatCount == 0 &&
                navController.currentDestination?.id == R.id.fragment_editor &&
                !activityModel.isAppLocked.value
            ) {
                noteQuickSwitchManager.currentTargetNoteId.value?.let { targetNoteId ->
                    navigateToQuickSwitchNote(targetNoteId)
                }
            }

            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (activityModel.isAppLocked.value) {
            requestUnlock()
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            noteQuickSwitchManager.clearPendingBinding()
        }
        if (!isChangingConfigurations) {
            schedulePrivacyReturnIfNeeded()
        }
        if (!isChangingConfigurations && activityModel.isAppLockEnabled.value) {
            activityModel.lockApp()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_PRIVACY_RETURN_HOME, pendingPrivacyReturnToHome)
        super.onSaveInstanceState(outState)
    }

    /**
     * Copies shared media from a URI to the app's private storage.
     * @param uri The source URI of the media file to copy
     * @return The new URI in app's private storage, or null if copy failed
     */
    private suspend fun copySharedMedia(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val mimeType = contentResolver.getType(uri) ?: return@withContext null
            activityModel.copyMediaToPrivateStorage(uri, mimeType)
        } catch (e: Exception) {
            null
        }
    }

    fun requestUnlock() {
        if (!activityModel.isAppLocked.value || isUnlockPromptShowing) return

        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> showUnlockPrompt()
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                isUnlockPromptShowing = false
                toaster.showLong(getString(R.string.message_app_lock_no_biometrics_enrolled))
            }
            else -> {
                isUnlockPromptShowing = false
                toaster.showLong(getString(R.string.message_app_lock_unavailable))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showUnlockPrompt() {
        isUnlockPromptShowing = true
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlockPromptShowing = false
                    activityModel.unlockApp()
                    pendingIntentAfterUnlock?.let { deferredIntent ->
                        pendingIntentAfterUnlock = null
                        handleIntent(deferredIntent, bypassLock = true)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isUnlockPromptShowing = false
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        toaster.showLong(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    toaster.showLong(getString(R.string.message_app_unlock_failed))
                }
            }
        )

        biometricPrompt?.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.title_app_unlock))
                .setSubtitle(getString(R.string.subtitle_app_unlock))
                .setNegativeButtonText(getString(R.string.action_cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    private fun handleIntent(intent: Intent, bypassLock: Boolean = false) {
        if (!bypassLock && activityModel.isAppLocked.value) {
            val shouldDeferIntent = intent.action != null || intent.data != null || intent.hasExtra("noteId")
            if (shouldDeferIntent) {
                pendingIntentAfterUnlock = Intent(intent)
            }
            requestUnlock()
            return
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val title = intent.getStringExtra(Intent.EXTRA_TITLE) ?: ""
                val content = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                var uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                // Try getting URI from ClipData if not in EXTRA_STREAM
                if (uri == null && intent.clipData != null && intent.clipData!!.itemCount > 0) {
                    uri = intent.clipData!!.getItemAt(0).uri
                }

                lifecycleScope.launch {
                    val args = bundleOf(
                        "transitionName" to "",
                        "newNoteTitle" to title,
                        "newNoteContent" to content,
                    )

                    if (uri != null) {
                        val newUri = copySharedMedia(uri)
                        if (newUri != null) {
                            withContext(Dispatchers.IO) {
                                val attachment = Attachment.fromUri(this@MainActivity, newUri)
                                args.putParcelableArray("newNoteAttachments", arrayOf(attachment))
                            }
                        }
                    }
                    navController.handleDeepLink(getDeepLink(args))
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                var uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }

                // Try getting URIs from ClipData if not in EXTRA_STREAM
                if (uris == null && intent.clipData != null) {
                    uris = ArrayList()
                    for (i in 0 until intent.clipData!!.itemCount) {
                        intent.clipData!!.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }

                if (!uris.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val args = bundleOf(
                            "transitionName" to "",
                            "newNoteTitle" to (intent.getStringExtra(Intent.EXTRA_TITLE) ?: ""),
                            "newNoteContent" to (intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""),
                        )

                        withContext(Dispatchers.IO) {
                            val attachments = uris.mapNotNull { uri ->
                                copySharedMedia(uri)?.let { newUri ->
                                    Attachment.fromUri(this@MainActivity, newUri)
                                }
                            }.toTypedArray()

                            if (attachments.isNotEmpty()) {
                                args.putParcelableArray("newNoteAttachments", attachments)
                            }
                        }
                        navController.handleDeepLink(getDeepLink(args))
                    }
                }
            }

            "org.qosp.notes.NEW_NOTE" -> {
                // Request to create a new note from the widget
                val args = bundleOf("transitionName" to "", "newNoteTitle" to "", "newNoteContent" to "")
                navController.handleDeepLink(getDeepLink(args))
                return
            }

            else -> {
                // Request to open a note from the widget
                val noteId = intent.getLongExtra("noteId", -1L)
                if (noteId > 0) {
                    val args = bundleOf("noteId" to noteId, "transitionName" to "")
                    navController.handleDeepLink(getDeepLink(args))
                    return
                }

                val hasNavigationDeepLink = intent.data != null ||
                    intent.hasExtra("android-support-nav:controller:deepLinkIds")

                if (hasNavigationDeepLink) {
                    navController.handleDeepLink(intent)
                }
            }
        }
    }

    private fun setupBackNavigation() {
        backCallback = onBackPressedDispatcher.addCallback(this, false) {
            if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
                if (navController.currentDestination?.id in drawerPersistentDestinations) {
                    returnToDrawerDestinationKeepingOpen(drawerReturnDestinationId ?: R.id.fragment_main)
                    return@addCallback
                }
                binding.drawer.closeDrawer(GravityCompat.START)
            }
            updateBackCallbackState()
        }

        binding.drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                updateBackCallbackState()
            }

            override fun onDrawerClosed(drawerView: View) {
                updateBackCallbackState()
            }
        })
        updateBackCallbackState()
    }

    private fun updateBackCallbackState() {
        if (!::backCallback.isInitialized) return
        backCallback.isEnabled = binding.drawer.isDrawerOpen(GravityCompat.START)
    }

    private fun navigateToQuickSwitchNote(noteId: Long) {
        navController.navigate(
            R.id.fragment_editor,
            bundleOf(
                "noteId" to noteId,
                "transitionName" to "",
            ),
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.fragment_editor, true)
                .build()
            )
    }

    private fun currentNavFragment() =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
            ?.childFragmentManager
            ?.primaryNavigationFragment

    private fun currentEditorFragment(): EditorFragment? = currentNavFragment() as? EditorFragment

    private fun isPrivacyHomeVisible(): Boolean =
        navController.currentDestination?.id == R.id.fragment_main && activityModel.isPrivacyPageActive.value

    private fun isPrivacyEditorVisible(): Boolean =
        navController.currentDestination?.id == R.id.fragment_editor &&
            currentEditorFragment()?.isPrivateNoteEditor() == true

    private fun isPrivacySearchVisible(): Boolean =
        navController.currentDestination?.id == R.id.fragment_search &&
            navController.currentBackStackEntry?.arguments?.getBoolean("isPrivatePage") == true

    private fun isSecureWindowNeeded(): Boolean =
        isPrivacyHomeVisible() || isPrivacyEditorVisible() || isPrivacySearchVisible()

    fun refreshSecureWindowFlag() {
        updateSecureWindowFlag()
    }

    private fun updateSecureWindowFlag() {
        val shouldEnable = isSecureWindowNeeded()
        if (isSecureWindowEnabled == shouldEnable) return

        isSecureWindowEnabled = shouldEnable
        if (shouldEnable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun schedulePrivacyReturnIfNeeded() {
        privacyReturnJob?.cancel()
        if (!isPrivacyHomeVisible() && !isPrivacyEditorVisible()) {
            return
        }

        currentEditorFragment()?.persistCurrentInputState()

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val isLocked = keyguardManager?.isKeyguardLocked == true
        if (isLocked) {
            preparePrivacyReturnToHome()
            return
        }

        privacyReturnJob = lifecycleScope.launch {
            delay(PRIVACY_RETURN_DELAY_MS)
            preparePrivacyReturnToHome()
        }
    }

    private fun applyPendingPrivacyReturnToHome() {
        if (!pendingPrivacyReturnToHome) {
            hidePrivacyReturnCover()
            return
        }

        showPrivacyReturnCover()
        currentEditorFragment()?.persistCurrentInputState()
        pendingPrivacyReturnToHome = false
        activityModel.setPrivacyPageActive(false)

        when (navController.currentDestination?.id) {
            R.id.fragment_editor -> {
                if (currentEditorFragment()?.isPrivateNoteEditor() == true) {
                    val returnedToMain = navController.popBackStack(R.id.fragment_main, false)
                    if (!returnedToMain) {
                        navController.navigate(
                            R.id.fragment_main,
                            null,
                            NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setPopUpTo(R.id.fragment_editor, true)
                                .setEnterAnim(0)
                                .setExitAnim(0)
                                .setPopEnterAnim(0)
                                .setPopExitAnim(0)
                                .build()
                        )
                    }
                }
            }

            R.id.fragment_privacy -> returnToMainFromPrivacyPage()
            R.id.fragment_main -> returnToMainFromPrivacyPage()
        }

        hidePrivacyReturnCoverWhenReady()
    }

    private fun preparePrivacyReturnToHome() {
        if (!isPrivacyHomeVisible() && !isPrivacyEditorVisible()) {
            return
        }

        currentEditorFragment()?.persistCurrentInputState()
        pendingPrivacyReturnToHome = true
        showPrivacyReturnCover()
    }

    private fun showPrivacyReturnCover() {
        if (privacyReturnCover != null || !::binding.isInitialized) return

        val background = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, background, true)

        privacyReturnCover = View(this).apply {
            if (background.resourceId != 0) {
                setBackgroundResource(background.resourceId)
            } else {
                setBackgroundColor(background.data)
            }
        }

        binding.root.addView(
            privacyReturnCover,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        privacyReturnCover?.bringToFront()
    }

    private fun hidePrivacyReturnCoverWhenReady() {
        privacyCoverHideJob?.cancel()
        privacyCoverHideJob = lifecycleScope.launch {
            var attempts = 0
            while (!isDefaultHomeVisible()) {
                if (++attempts == PRIVACY_RETURN_HOME_FORCE_ATTEMPTS) {
                    forceDefaultHome()
                }
                delay(PRIVACY_RETURN_HOME_CHECK_INTERVAL_MS)
            }

            delay(PRIVACY_RETURN_COVER_HIDE_DELAY_MS)
            if (isDefaultHomeVisible()) {
                hidePrivacyReturnCover()
            } else {
                hidePrivacyReturnCoverWhenReady()
            }
        }
    }

    private fun hidePrivacyReturnCover() {
        privacyCoverHideJob?.cancel()
        privacyCoverHideJob = null
        val cover = privacyReturnCover ?: return
        (cover.parent as? ViewGroup)?.removeView(cover)
        privacyReturnCover = null
    }

    private fun isDefaultHomeVisible(): Boolean =
        navController.currentDestination?.id == R.id.fragment_main &&
            !activityModel.isPrivacyPageActive.value

    private fun forceDefaultHome() {
        activityModel.setPrivacyPageActive(false)
        if (navController.currentDestination?.id == R.id.fragment_main) return

        val returnedToMain = navController.popBackStack(R.id.fragment_main, false)
        if (!returnedToMain) {
            navController.navigate(
                R.id.fragment_main,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setEnterAnim(0)
                    .setExitAnim(0)
                    .setPopEnterAnim(0)
                    .setPopExitAnim(0)
                    .build()
            )
        }
    }

    private fun registerScreenOffReceiver() {
        if (isScreenOffReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isScreenOffReceiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!isScreenOffReceiverRegistered) return
        unregisterReceiver(screenOffReceiver)
        isScreenOffReceiverRegistered = false
    }

    private fun returnToMainFromPrivacyPage() {
        activityModel.setPrivacyPageActive(false)

        if (navController.currentDestination?.id == R.id.fragment_privacy) {
            val returnedToMain = navController.popBackStack(R.id.fragment_main, false)
            if (!returnedToMain) {
                navController.navigate(
                    R.id.fragment_main,
                    null,
                    NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.fragment_privacy, true)
                        .setEnterAnim(0)
                        .setExitAnim(0)
                        .setPopEnterAnim(0)
                        .setPopExitAnim(0)
                        .build()
                )
            }
        }
    }

    private fun returnToDrawerDestinationKeepingOpen(destinationId: Int) {
        val returned = navController.popBackStack(destinationId, false)
        if (!returned) {
            navController.navigate(
                destinationId,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()
            )
        }
    }

    private fun getDeepLink(args: Bundle): Intent? = NavDeepLinkBuilder(this@MainActivity)
        .setGraph(R.navigation.nav_graph)
        .setDestination(R.id.fragment_editor)
        .setArguments(args)
        .createTaskStackBuilder()
        .first()

    private fun setupDrawerHeader() {
        val header = binding.navigationView.getHeaderView(0)
        val syncSettingsButton =
            header.findViewById<AppCompatImageButton>(R.id.button_sync_settings)
        val textViewUsername = header.findViewById<AppCompatTextView>(R.id.text_view_username)
        val textViewProvider = header.findViewById<AppCompatTextView>(R.id.text_view_provider)

        // Fixes bug that causes the header to have large padding when the keyboard is open
        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            header.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        syncSettingsButton.setOnClickListener {
            binding.drawer.closeAndThen {
                navController.navigateSafely(R.id.fragment_sync_settings)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                backendProvider.syncProvider.collect { backend ->
                    when (backend?.type) {
                        CloudService.NEXTCLOUD -> {
                            NextcloudConfig.fromPreferences(preferenceRepository)
                                .filterNotNull().firstOrNull()?.let { config ->
                                    textViewUsername.text = config.username
                                    textViewProvider.text = getString(R.string.preferences_cloud_service_nextcloud)
                                }
                        }

                        CloudService.FILE_STORAGE -> {
                            val uri = StorageConfig.storageLocation(preferenceRepository)
                                .filterNotNull().firstOrNull()?.location
                            if (uri != null && uri.toString().isNotEmpty()) {
                                textViewUsername.text =
                                    uri.toFriendlyString(applicationContext)
                                textViewProvider.text = getString(R.string.preferences_cloud_service_files)
                            } else {
                                textViewUsername.text = getString(R.string.preferences_cloud_service)
                                textViewProvider.text = getString(R.string.preferences_cloud_service_disabled)
                            }
                        }

                        CloudService.DISABLED, null -> {
                            textViewUsername.text = getString(R.string.preferences_cloud_service)
                            textViewProvider.text = getString(R.string.preferences_cloud_service_disabled)
                        }
                    }
                }
            }
        }
    }

    private fun selectCurrentDestinationMenuItem(
        destinationId: Int? = null,
        arguments: Bundle? = null
    ) {
        val destinationId =
            when (val id = destinationId ?: navController.currentDestination?.id ?: return) {
                // Assign destinations that do not have a drawer entry to an existing entry
                R.id.fragment_privacy -> R.id.fragment_main
                R.id.fragment_sync_settings -> R.id.fragment_settings
                R.id.fragment_search -> R.id.fragment_main
                else -> id
            }

        val arguments = arguments ?: navController.currentBackStackEntry?.arguments
        val notebookId = arguments?.getLong("notebookId", -1L)?.takeIf { it >= 0L }

        binding.navigationView.post {
            ((notebooksMenu?.children ?: emptySequence()) + topLevelMenu.children)
                .forEach { item ->
                    item.isChecked = when (notebookId) {
                        null -> item.itemId == destinationId
                        else -> item.itemId == notebookId.toInt()
                    }
                }
        }
    }

    private fun observePrivacyPageEntryCode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferenceRepository.getEncryptedString(PreferenceRepository.PRIVACY_PAGE_ENTRY_CODE)
                    .collect { code ->
                        privacyPageEntryCode = code.trim()
                    }
            }
        }
    }

    private fun observePrivacyWindowSecurity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                activityModel.isPrivacyPageActive.collect {
                    updateSecureWindowFlag()
                }
            }
        }
    }

    private fun armHiddenDoor() {
        hiddenDoorWebsiteJob?.cancel()
        hiddenDoorExpiryJob?.cancel()
        isHiddenDoorArmed = true
        hiddenDoorInput = ""
        hiddenDoorWebsiteJob = lifecycleScope.launch {
            delay(HIDDEN_DOOR_WEBSITE_DELAY_MS)
            if (isHiddenDoorArmed && hiddenDoorInput.isBlank()) {
                disarmHiddenDoor()
                binding.drawer.closeAndThen {
                    launchWebsite()
                }
            }
        }
        hiddenDoorExpiryJob = lifecycleScope.launch {
            delay(HIDDEN_DOOR_PRIVACY_WINDOW_MS)
            disarmHiddenDoor()
        }
    }

    private fun disarmHiddenDoor() {
        hiddenDoorWebsiteJob?.cancel()
        hiddenDoorWebsiteJob = null
        hiddenDoorExpiryJob?.cancel()
        hiddenDoorExpiryJob = null
        isHiddenDoorArmed = false
        hiddenDoorInput = ""
    }

    private fun launchWebsite() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.app_website)))
        runCatching { startActivity(intent) }
    }

    private fun maybeHandleHiddenDoorInput(itemId: Int): Boolean {
        if (itemId == R.id.action_hidden_other) {
            armHiddenDoor()
            return true
        }

        if (!isHiddenDoorArmed) {
            return false
        }

        val digit = hiddenDoorSequenceItemIds.indexOf(itemId)
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: return true

        val digitText = digit.toString()
        val nextInput = hiddenDoorInput + digitText
        hiddenDoorInput = when {
            privacyPageEntryCode.startsWith(nextInput) -> nextInput
            privacyPageEntryCode.startsWith(digitText) -> digitText
            else -> ""
        }

        if (hiddenDoorInput == privacyPageEntryCode && hiddenDoorInput.isNotEmpty()) {
            disarmHiddenDoor()
            binding.drawer.closeAndThen {
                activatePrivacyPage()
            }
        }

        return true
    }

    private fun activatePrivacyPage() {
        activityModel.setPrivacyPageActive(true)

        if (navController.currentDestination?.id == R.id.fragment_main) {
            return
        }

        val returnedToMain = navController.popBackStack(R.id.fragment_main, false)
        if (!returnedToMain) {
            navController.navigate(
                R.id.fragment_main,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setEnterAnim(0)
                    .setExitAnim(0)
                    .setPopEnterAnim(0)
                    .setPopExitAnim(0)
                    .build()
            )
        }
    }

    private fun setupDrawerMenuItems() {
        // Alternative of setupWithNavController(), NavigationUI.java
        // Sets up click listeners for all drawer menu items except from notebooks.
        // Those are handled in createNotebookMenuItems()
        (topLevelMenu.children + listOfNotNull(notebooksMenu?.findItem(R.id.fragment_manage_notebooks)))
            .forEach { item ->
                if (item.itemId !in primaryDestinations + secondaryDestinations + R.id.action_hidden_other) return@forEach

                item.setOnMenuItemClickListener {
                    if (maybeHandleHiddenDoorInput(item.itemId)) {
                        return@setOnMenuItemClickListener false
                    }

                    if (item.itemId in drawerPersistentDestinations) {
                        drawerReturnDestinationId = navController.currentDestination?.id
                            ?.takeIf { it in primaryDestinations }
                            ?: R.id.fragment_main
                        binding.drawer.closeAndThen {
                            navController.navigate(
                                item.itemId,
                                null,
                                NavOptions.Builder()
                                    .setLaunchSingleTop(true)
                                    .setPopUpTo(R.id.fragment_main, false)
                                    .build()
                            )
                        }
                    } else {
                        binding.drawer.closeAndThen {
                            navController.navigateSafely(item.itemId)
                        }
                    }
                    false // Returning true would cause the menu item to become checked.
                    // We check the menu items only when the destination changes.
                }
            }

        notebooksMenu?.findItem(R.id.nav_default_notebook)?.setOnMenuItemClickListener {
            if (maybeHandleHiddenDoorInput(R.id.nav_default_notebook)) {
                return@setOnMenuItemClickListener false
            }
            binding.drawer.closeAndThen {
                navController.navigateSafely(
                    R.id.fragment_notebook,
                    bundleOf(
                        "notebookId" to R.id.nav_default_notebook.toLong(),
                        "notebookName" to getString(R.string.default_notebook),
                    )
                )
            }
            false // Returning true would cause the menu item to become checked.
            // We check the menu items only when the destination changes.
        }
    }

    private fun setupNavigation() {
        fun createNotebookMenuItems(notebooks: List<Notebook>) {

            // Obtaining the current setting of notebook sorting order.
            val sort = runBlocking {
                return@runBlocking preferenceRepository
                    .getAll()
                    .map { it.sortNavdrawerNotebooksMethod }
                    .first()
                    .name
            }

            // Sorting the notebooks.
            val sortedNotebooks: List<Notebook> = when (sort) {
                SortNavdrawerNotebooksMethod.CREATION_ASC.name -> notebooks.sortedBy { it.id }
                SortNavdrawerNotebooksMethod.CREATION_DESC.name -> notebooks.sortedByDescending { it.id }
                SortNavdrawerNotebooksMethod.TITLE_ASC.name -> notebooks.sortedBy { it.name }
                SortNavdrawerNotebooksMethod.TITLE_DESC.name -> notebooks.sortedByDescending { it.name }
                else -> notebooks.sortedBy { it.name }
            }

            // Displaying the notebooks.
            sortedNotebooks.forEach { notebook ->
                val menuItem = notebooksMenu?.findItem(notebook.id.toInt())
                if (menuItem != null && notebook.name != menuItem.title) {
                    menuItem.title = notebook.name
                }
                if (menuItem == null) {
                    notebooksMenu?.add(
                        R.id.section_notebooks,
                        notebook.id.toInt(),
                        0,
                        notebook.name
                    )
                        ?.setIcon(R.drawable.ic_notebook)
                        ?.setCheckable(true)
                        ?.setOnMenuItemClickListener {
                            if (maybeHandleHiddenDoorInput(notebook.id.toInt())) {
                                return@setOnMenuItemClickListener false
                            }
                            binding.drawer.closeAndThen {
                                navController.navigateSafely(
                                    R.id.fragment_notebook,
                                    bundleOf(
                                        "notebookId" to notebook.id,
                                        "notebookName" to notebook.name,
                                    )
                                )
                            }
                            false // Returning true would cause the menu item to become checked.
                            // We check the menu items only when the destination changes.
                        }
                }
            }

            selectCurrentDestinationMenuItem()
        }

        appBarConfiguration = AppBarConfiguration(
            primaryDestinations,
            binding.drawer
        )

        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

        setupDrawerMenuItems()

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            if (destination.id == R.id.fragment_privacy) {
                activityModel.setPrivacyPageActive(true)
            }

            currentFocus?.hideKeyboard()
            selectCurrentDestinationMenuItem(destination.id, arguments)

            setDrawerEnabled(destination.id !in drawerHiddenDestinations)
            updateBackCallbackState()
            binding.drawer.post {
                updateSecureWindowFlag()
            }

            if (destination.id == drawerReturnDestinationId && drawerReturnDestinationId != null) {
                binding.drawer.post {
                    binding.drawer.openDrawer(GravityCompat.START)
                    updateBackCallbackState()
                }
                drawerReturnDestinationId = null
            }
        }

        activityModel.notebooks.collect(this) { (showDefaultNotebook, notebooks) ->
            val notebookIds = (notebooks.map { it.id.toInt() } + R.id.nav_default_notebook).toSet()

            // Remove deleted notebooks from the menu
            (primaryDestinations + secondaryDestinations + notebookIds).let { dests ->
                notebooksMenu?.let { nbMenu ->
                    var index = 0
                    while (index < nbMenu.size()) {
                        val item = nbMenu[index]
                        if (item.itemId !in dests) nbMenu.removeItem(item.itemId) else index++
                    }
                }
            }

            createNotebookMenuItems(notebooks)

            val defaultTitle = getString(R.string.default_notebook)
            notebooksMenu?.findItem(R.id.nav_default_notebook)?.apply {
                isVisible = showDefaultNotebook
                title =
                    defaultTitle + " (${getString(R.string.default_string)})".takeIf { notebooks.any { it.name == defaultTitle } }
                        .orEmpty()
            }
        }
    }

    private fun setDrawerEnabled(enabled: Boolean) {
        binding.drawer.setDrawerLockMode(
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        )
    }

    fun isDrawerOpen(): Boolean = binding.drawer.isDrawerOpen(GravityCompat.START)

    fun startBackup(backupUri: Uri) {
        BackupService.backupNotes(this, activityModel.notesToBackup, backupUri)
    }

    fun restoreNotes(backupUri: Uri) {
        BackupService.restoreNotes(this, backupUri)
    }

    private companion object {
        private const val PRIVACY_RETURN_DELAY_MS = 0L
        private const val HIDDEN_DOOR_WEBSITE_DELAY_MS = 2_000L
        private const val HIDDEN_DOOR_PRIVACY_WINDOW_MS = 2_500L
        private const val PRIVACY_RETURN_COVER_HIDE_DELAY_MS = 180L
        private const val PRIVACY_RETURN_HOME_CHECK_INTERVAL_MS = 16L
        private const val PRIVACY_RETURN_HOME_FORCE_ATTEMPTS = 30
        private const val STATE_PENDING_PRIVACY_RETURN_HOME = "state_pending_privacy_return_home"
    }
}
