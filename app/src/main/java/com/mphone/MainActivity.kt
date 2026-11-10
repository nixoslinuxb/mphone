package com.mphone

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricTeal
import com.mphone.ui.theme.ElectricViolet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mphone.data.CallEvent
import com.mphone.data.CallEventBus
import com.mphone.data.CallLogRepository
import com.mphone.data.ContactMerger
import com.mphone.data.CorporateCallerIdRegistry
import com.mphone.data.ContactsRepository
import com.mphone.data.CountryCodeFilter
import com.mphone.data.SendStatus
import com.mphone.data.SmsContentObserver
import com.mphone.data.SmsEventBus
import com.mphone.ui.splash.SplashScreen
import com.mphone.data.SmsRepository
import com.mphone.data.TelecomCallManager
import com.mphone.data.TurkeySpamNumberBlocker
import com.mphone.model.CallType
import com.mphone.model.Contact
import com.mphone.model.SmsMessage
import com.mphone.model.SmsThread
import com.mphone.service.DynamicIslandNotificationHelper
import com.mphone.ui.components.AppBottomBar
import com.mphone.ui.components.DefaultRoleBanner
import com.mphone.ui.components.DynamicIslandNotification
import com.mphone.ui.components.DynamicIslandState
import com.mphone.ui.components.MainTab
import com.mphone.ui.components.PermissionBanner
import com.mphone.ui.components.SearchHeaderBar
import com.mphone.ui.contacts.AddContactDialog
import com.mphone.ui.contacts.ContactDetailSheet
import com.mphone.ui.contacts.ContactsScreen
import com.mphone.ui.contacts.EditContactDialog
import com.mphone.ui.phone.ActiveCallScreen
import com.mphone.ui.phone.IncomingCallScreen
import com.mphone.ui.phone.OutgoingCallScreen
import com.mphone.ui.phone.PhoneScreen
import com.mphone.ui.sms.ChatScreen
import com.mphone.ui.sms.NewMessageDialog
import com.mphone.ui.sms.SmsListScreen
import com.mphone.ui.theme.MphoneTheme

class MainActivity : ComponentActivity() {

    private lateinit var contactsRepo: ContactsRepository
    private lateinit var callLogRepo: CallLogRepository
    private lateinit var smsRepo: SmsRepository

    private var hasPermissions by mutableStateOf(false)
    private var isDefaultDialer by mutableStateOf(false)
    private var isDefaultSms by mutableStateOf(false)
    private var isDefaultCallScreening by mutableStateOf(false)
    private var initialSmsSender by mutableStateOf<String?>(null)
    private var initialIncomingCall by mutableStateOf<Pair<String, String>?>(null)

    private val permissionsToRequest = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkAndLoadData()
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkRoleStatuses()
    }

    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkRoleStatuses()
    }

    private val callScreeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkRoleStatuses()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        contactsRepo = ContactsRepository(applicationContext)
        callLogRepo = CallLogRepository(applicationContext)
        smsRepo = SmsRepository(applicationContext)

        checkAndLoadData()
        checkRoleStatuses()
        handleNotificationLaunchIntent(intent)

        setContent {
            MphoneTheme {
                val isCallLaunch = remember(intent) {
                    intent.hasExtra("incoming_call_number") || !intent.getStringExtra("incoming_call_number").isNullOrBlank()
                }
                var showSplash by remember { mutableStateOf(!isCallLaunch) }

                if (showSplash && !isCallLaunch) {
                    SplashScreen(
                        onSplashFinished = {
                            showSplash = false
                        }
                    )
                } else {
                    MainAppScreen(
                        contactsRepo = contactsRepo,
                        callLogRepo = callLogRepo,
                        smsRepo = smsRepo,
                        hasPermissions = hasPermissions,
                        isDefaultDialer = isDefaultDialer,
                        isDefaultSms = isDefaultSms,
                        isDefaultCallScreening = isDefaultCallScreening,
                        initialSmsSender = initialSmsSender,
                        initialIncomingCall = initialIncomingCall,
                        onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) },
                        onRequestDefaultDialer = ::requestDefaultDialerRole,
                        onRequestDefaultSms = ::requestDefaultSmsRole,
                        onRequestDefaultCallScreening = ::requestCallScreeningRole,
                        onPerformCall = ::performCall,
                        onResetSmsSender = ::resetInitialSmsSender
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        handleNotificationLaunchIntent(intent)
    }

    private fun handleNotificationLaunchIntent(launchIntent: Intent?) {
        val sender = launchIntent?.getStringExtra("sms_sender")
        if (!sender.isNullOrBlank()) {
            initialSmsSender = sender
        }

        val incomingNumber = launchIntent?.getStringExtra("incoming_call_number")
        if (!incomingNumber.isNullOrBlank()) {
            val matchedContact = ContactMerger.findContactForNumber(
                contactsRepo.contacts.value,
                incomingNumber
            )
            val corporateName = CorporateCallerIdRegistry.getCorporateName(incomingNumber)
            val displayName = matchedContact?.name ?: corporateName ?: incomingNumber
            initialIncomingCall = Pair(displayName, incomingNumber)
        }
    }

    fun resetInitialSmsSender() {
        initialSmsSender = null
    }

    private fun checkRoleStatuses() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            isDefaultSms = roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            isDefaultCallScreening = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            val telecomManager = getSystemService(TelecomManager::class.java)
            isDefaultDialer = telecomManager?.defaultDialerPackage == packageName
            isDefaultSms = Telephony.Sms.getDefaultSmsPackage(this) == packageName
            isDefaultCallScreening = true
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                dialerRoleLauncher.launch(intent)
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            dialerRoleLauncher.launch(intent)
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                smsRoleLauncher.launch(intent)
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            smsRoleLauncher.launch(intent)
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                callScreeningRoleLauncher.launch(intent)
            }
        }
    }

    private fun checkAndLoadData() {
        hasPermissions = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        contactsRepo.loadContacts()
        callLogRepo.loadCallLogs(contactsRepo.contacts.value)
        smsRepo.loadSmsThreads(contactsRepo.contacts.value)
    }

    private fun performCall(
        name: String,
        number: String,
        subId: Int? = null,
        onBlockAlert: ((title: String, reason: String) -> Unit)? = null
    ) {
        if (CountryCodeFilter.isFromOutsideTurkey(number)) {
            val reason = CountryCodeFilter.getBlockReason(number)
                ?: "Call blocked: Numbers outside of Türkiye (+90) are automatically blocked."
            onBlockAlert?.invoke("International Call Blocked", reason)
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return
        }

        if (TurkeySpamNumberBlocker.isTurkishSpamNumber(number)) {
            val reason = TurkeySpamNumberBlocker.getSpamBlockReason(number)
                ?: "Call blocked: Flagged in Türkiye Spam & Robocall Database."
            onBlockAlert?.invoke("Spam Call Blocked", reason)
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        try {
            val uri = Uri.fromParts("tel", number, null)
            val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
            val phoneAccounts = try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager?.callCapablePhoneAccounts
                } else null
            } catch (_: Exception) { null }

            val targetAccount = if (subId != null && !phoneAccounts.isNullOrEmpty()) {
                phoneAccounts.find { handle ->
                    handle.id.contains(subId.toString())
                } ?: phoneAccounts.firstOrNull()
            } else {
                phoneAccounts?.firstOrNull()
            }

            val extras = Bundle()
            if (targetAccount != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetAccount)
            }

            if (hasCallPermission) {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    putExtras(extras)
                }
                startActivity(intent)
            } else {
                val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                    putExtras(extras)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Dialing $number", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainAppScreen(
    contactsRepo: ContactsRepository,
    callLogRepo: CallLogRepository,
    smsRepo: SmsRepository,
    hasPermissions: Boolean,
    isDefaultDialer: Boolean,
    isDefaultSms: Boolean,
    isDefaultCallScreening: Boolean,
    initialSmsSender: String?,
    initialIncomingCall: Pair<String, String>?,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onRequestDefaultCallScreening: () -> Unit,
    onPerformCall: (name: String, number: String, subId: Int?, onBlockAlert: ((title: String, reason: String) -> Unit)?) -> Unit,
    onResetSmsSender: () -> Unit = {}
) {
    val contacts by contactsRepo.contacts.collectAsState()
    val callLogs by callLogRepo.callLogs.collectAsState()
    val spamCallLogs by callLogRepo.spamCallLogs.collectAsState()
    val hasUnreadSpamCalls by callLogRepo.hasUnreadSpamCalls.collectAsState()
    val smsThreads by smsRepo.threads.collectAsState()
    val spamThreads by smsRepo.spamThreads.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.PHONE) }
    var searchQuery by remember { mutableStateOf("") }
    var islandState by remember { mutableStateOf<DynamicIslandState>(DynamicIslandState.Idle) }

    var initialAddContactNumber by remember { mutableStateOf("") }

    // Dialog & Sheet States
    var selectedContactForDetail by remember { mutableStateOf<Contact?>(null) }
    var contactToEditFromDetail by remember { mutableStateOf<Contact?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showNewMessageDialog by remember { mutableStateOf(false) }

    // Navigation Sub-screens
    var activeChatThreadId by remember { mutableStateOf<String?>(null) }
    var activeCallData by remember { mutableStateOf<Pair<String, String>?>(null) } // Name, Number
    var isOutgoingCall by remember { mutableStateOf(false) }
    var isCallAnswered by remember { mutableStateOf(false) }
    var isCallMinimized by remember { mutableStateOf(false) }

    val unreadSmsCount = remember(smsThreads) { smsThreads.sumOf { it.unreadCount } }
    val unreadMissedCallCount by callLogRepo.unreadMissedCallCount.collectAsState()
    val blockedNumbers by callLogRepo.blockedNumbers.collectAsState()

    LaunchedEffect(isDefaultDialer) {
        if (!isDefaultDialer) {
            onRequestDefaultDialer()
        }
    }

    LaunchedEffect(contacts) {
        if (contacts.isNotEmpty()) {
            smsRepo.loadSmsThreads(contacts)
            callLogRepo.loadCallLogs(contacts)
        }
    }

    LaunchedEffect(Unit) {
        SmsEventBus.incomingSmsEvent.collect { (sender, body) ->
            val matchedContact = ContactMerger.findContactForNumber(contactsRepo.contacts.value, sender)
            val corporateName = CorporateCallerIdRegistry.getCorporateName(sender)
            val displayName = matchedContact?.name ?: corporateName ?: sender
            val cleanSender = ContactMerger.normalizePhoneNumber(sender)
            smsRepo.receiveIncomingSms(sender, body, contactsRepo.contacts.value)

            val currentActive = DynamicIslandNotificationHelper.activeChatNumber
            val cleanActive = if (!currentActive.isNullOrBlank()) ContactMerger.normalizePhoneNumber(currentActive) else ""

            if (cleanActive.isBlank() || cleanActive != cleanSender) {
                islandState = DynamicIslandState.SmsAlert(
                    senderName = displayName,
                    senderNumber = sender,
                    messageSnippet = body,
                    threadId = cleanSender
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        CallEventBus.callStateEvent.collect { event ->
            when (event) {
                is CallEvent.Connected -> {
                    if (activeCallData != null) {
                        isCallAnswered = true
                    }
                }
                is CallEvent.Ended -> {
                    val currentCall = activeCallData
                    if (currentCall != null) {
                        callLogRepo.addCallLog(
                            name = currentCall.first,
                            number = currentCall.second,
                            type = if (isOutgoingCall) CallType.OUTGOING else if (isCallAnswered) CallType.INCOMING else CallType.MISSED,
                            duration = "Completed"
                        )
                        activeCallData = null
                        isOutgoingCall = false
                        isCallAnswered = false
                        isCallMinimized = false
                        islandState = DynamicIslandState.Idle
                    }
                }
            }
        }
    }

    // Synchronous resolution of active chat thread ID on Frame 1 (0-frame delay, NO FLASH!)
    val effectiveActiveChatThreadId = remember(activeChatThreadId, initialSmsSender, smsThreads) {
        val sender = initialSmsSender
        if (!sender.isNullOrBlank()) {
            val senderClean = ContactMerger.normalizePhoneNumber(sender)
            val existingThread = smsThreads.find { thread ->
                thread.contactNumber == sender ||
                thread.contactName.equals(sender, ignoreCase = true) ||
                (senderClean.isNotBlank() && ContactMerger.normalizePhoneNumber(thread.contactNumber) == senderClean) ||
                (senderClean.length >= 7 && ContactMerger.normalizePhoneNumber(thread.contactNumber).length >= 7 &&
                        ContactMerger.normalizePhoneNumber(thread.contactNumber).takeLast(7) == senderClean.takeLast(7))
            }
            existingThread?.id ?: sender
        } else {
            activeChatThreadId
        }
    }

    LaunchedEffect(initialSmsSender, smsThreads) {
        val sender = initialSmsSender
        if (!sender.isNullOrBlank()) {
            currentTab = MainTab.MESSAGES
            if (activeChatThreadId != effectiveActiveChatThreadId) {
                activeChatThreadId = effectiveActiveChatThreadId
            }
            smsRepo.markThreadAsRead(effectiveActiveChatThreadId ?: "")
            onResetSmsSender()
        }
    }

    LaunchedEffect(initialIncomingCall) {
        val incomingCall = initialIncomingCall
        if (incomingCall != null && !isOutgoingCall) {
            activeCallData = incomingCall
            isOutgoingCall = false
            isCallAnswered = false
            isCallMinimized = false
        }
    }

    val context = LocalContext.current

    DisposableEffect(Unit) {
        val observer = SmsContentObserver(context) {
            smsRepo.loadSmsThreads(contacts)
        }
        observer.register()
        onDispose {
            observer.unregister()
        }
    }

    // Active / Outgoing / Incoming Call Screen Full Screen Overlay
    val currentCall = activeCallData
    if (currentCall != null && !isCallMinimized) {
        val matchedContact = ContactMerger.findContactForNumber(contacts, currentCall.second)
        val callerPhotoUri = matchedContact?.photoUri

        if (isOutgoingCall && !isCallAnswered) {
            OutgoingCallScreen(
                name = currentCall.first,
                number = currentCall.second,
                photoUri = callerPhotoUri,
                onCancelCall = {
                    TelecomCallManager.endOrDeclineCall(context)
                    callLogRepo.addCallLog(
                        name = currentCall.first,
                        number = currentCall.second,
                        type = CallType.OUTGOING,
                        duration = "0s (Canceled)"
                    )
                    activeCallData = null
                    isOutgoingCall = false
                    islandState = DynamicIslandState.Idle
                },
                onMinimize = {
                    isCallMinimized = true
                    islandState = DynamicIslandState.ActiveCall(
                        name = currentCall.first,
                        number = currentCall.second,
                        durationStr = "Calling..."
                    )
                },
                onCallAnswered = {
                    isCallAnswered = true
                }
            )
            return
        } else if (!isCallAnswered) {
            IncomingCallScreen(
                name = currentCall.first,
                number = currentCall.second,
                photoUri = callerPhotoUri,
                onAnswer = {
                    TelecomCallManager.acceptIncomingCall(context)
                    isCallAnswered = true
                },
                onDecline = {
                    TelecomCallManager.endOrDeclineCall(context)
                    callLogRepo.addCallLog(
                        name = currentCall.first,
                        number = currentCall.second,
                        type = CallType.REJECTED,
                        duration = "0s (Declined)"
                    )
                    activeCallData = null
                    isOutgoingCall = false
                    islandState = DynamicIslandState.Idle
                },
                onMinimize = {
                    isCallMinimized = true
                    islandState = DynamicIslandState.ActiveCall(
                        name = currentCall.first,
                        number = currentCall.second,
                        durationStr = "Ringing..."
                    )
                }
            )
            return
        } else {
            ActiveCallScreen(
                name = currentCall.first,
                number = currentCall.second,
                photoUri = callerPhotoUri,
                contacts = contacts,
                onPerformCall = { pName, pNumber ->
                    onPerformCall(pName, pNumber, null) { title, reason ->
                        islandState = DynamicIslandState.SpamBlockedAlert(title, reason)
                    }
                },
                onEndCall = { durationStr ->
                    TelecomCallManager.endOrDeclineCall(context)
                    callLogRepo.addCallLog(
                        name = currentCall.first,
                        number = currentCall.second,
                        type = CallType.OUTGOING,
                        duration = durationStr
                    )
                    activeCallData = null
                    isOutgoingCall = false
                    islandState = DynamicIslandState.Idle
                },
                onMinimize = {
                    isCallMinimized = true
                    islandState = DynamicIslandState.ActiveCall(
                        name = currentCall.first,
                        number = currentCall.second,
                        durationStr = "Ongoing Call"
                    )
                }
            )
            return
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B14),
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
    ) {
        // Ambient Top-Left Liquid Indigo Halo
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricIndigo.copy(alpha = 0.28f),
                            ElectricViolet.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Ambient Bottom-Right Liquid Teal/Violet Halo
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricViolet.copy(alpha = 0.25f),
                            ElectricTeal.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.statusBarsPadding()) {
                    SearchHeaderBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = when (currentTab) {
                            MainTab.PHONE -> "Search recents..."
                            MainTab.CONTACTS -> "Search contacts..."
                            MainTab.MESSAGES -> "Search messages..."
                        }
                    )
                    if (!hasPermissions) {
                        PermissionBanner(onRequestPermissions = onRequestPermissions)
                    }
                    if (!isDefaultDialer || !isDefaultSms || !isDefaultCallScreening) {
                        DefaultRoleBanner(
                            isDefaultDialer = isDefaultDialer,
                            isDefaultSms = isDefaultSms,
                            isDefaultCallScreening = isDefaultCallScreening,
                            onRequestDefaultDialer = onRequestDefaultDialer,
                            onRequestDefaultSms = onRequestDefaultSms,
                            onRequestDefaultCallScreening = onRequestDefaultCallScreening
                        )
                    }
                }
            },
            bottomBar = {
                AppBottomBar(
                    currentTab = currentTab,
                    onTabSelected = {
                        currentTab = it
                        searchQuery = ""
                    },
                    unreadSmsCount = unreadSmsCount,
                    missedCallCount = unreadMissedCallCount
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                label = "TabContent"
            ) { tab ->
                when (tab) {
                    MainTab.PHONE -> {
                        PhoneScreen(
                            callLogs = callLogs,
                            searchQuery = searchQuery,
                            onStartCall = { name, number, subId ->
                                val resolvedName = if (name.isBlank() || name == "Unknown" || name == number) {
                                    ContactMerger.findContactForNumber(contacts, number)?.name ?: name
                                } else name

                                onPerformCall(resolvedName, number, subId) { title, reason ->
                                    islandState = DynamicIslandState.SpamBlockedAlert(title, reason)
                                }
                                if (!CountryCodeFilter.isFromOutsideTurkey(number) && !TurkeySpamNumberBlocker.isTurkishSpamNumber(number)) {
                                    activeCallData = Pair(resolvedName, number)
                                    isOutgoingCall = true
                                    isCallAnswered = false
                                    isCallMinimized = false
                                }
                            },
                            spamCallLogs = spamCallLogs,
                            blockedNumbers = blockedNumbers,
                            contacts = contacts,
                            hasUnreadSpamCalls = hasUnreadSpamCalls,
                            hasUnreadMissedCalls = unreadMissedCallCount > 0,
                            onMarkSpamCallsAsRead = { callLogRepo.markSpamCallsAsRead() },
                            onMarkMissedCallsAsRead = { callLogRepo.markMissedCallsAsRead() },
                            onDeleteCallLog = { id -> callLogRepo.deleteCallLog(id) },
                            onDeleteCallLogsForNumber = { number, contactId -> callLogRepo.deleteCallLogsForNumber(number, contactId) },
                            onBlockNumber = { number -> callLogRepo.blockNumber(number) },
                            onUnblockNumber = { number -> callLogRepo.unblockNumber(number) },
                            onAddContactForNumber = { number ->
                                initialAddContactNumber = number
                                showAddContactDialog = true
                            },
                            onSmsClick = { number ->
                                val existingThread = smsThreads.find {
                                    it.contactNumber == number || it.contactName == number
                                }
                                if (existingThread != null) {
                                    activeChatThreadId = existingThread.id
                                } else {
                                    val (_, newId) = smsRepo.createThread(number, number)
                                    if (newId != null) {
                                        activeChatThreadId = newId
                                    }
                                }
                            }
                        )
                    }

                    MainTab.CONTACTS -> {
                        ContactsScreen(
                            contacts = contacts,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onContactClick = { contact -> selectedContactForDetail = contact },
                            onCallClick = { contact ->
                                onPerformCall(contact.name, contact.phoneNumber, null) { title, reason ->
                                    islandState = DynamicIslandState.SpamBlockedAlert(title, reason)
                                }
                                if (!CountryCodeFilter.isFromOutsideTurkey(contact.phoneNumber) && !TurkeySpamNumberBlocker.isTurkishSpamNumber(contact.phoneNumber)) {
                                    activeCallData = Pair(contact.name, contact.phoneNumber)
                                    isOutgoingCall = true
                                    isCallAnswered = false
                                    isCallMinimized = false
                                }
                            },
                            onSmsClick = { contact ->
                                val existingThread = smsThreads.find {
                                    it.contactNumber == contact.phoneNumber || it.contactName == contact.name
                                }
                                if (existingThread != null) {
                                    activeChatThreadId = existingThread.id
                                } else {
                                    val (_, newId) = smsRepo.createThread(contact.name, contact.phoneNumber)
                                    if (newId != null) {
                                        activeChatThreadId = newId
                                    }
                                }
                            },
                            onAddContactClick = { showAddContactDialog = true },
                            onEditContact = { id, name, number, photoUri, note ->
                                contactsRepo.updateContact(id, name, number, photoUri, note)
                                val currentContacts = contactsRepo.contacts.value
                                callLogRepo.loadCallLogs(currentContacts)
                                smsRepo.loadSmsThreads(currentContacts)
                            },
                            onDeleteContact = { id, number ->
                                contactsRepo.deleteContact(id, number)
                                val currentContacts = contactsRepo.contacts.value
                                callLogRepo.loadCallLogs(currentContacts)
                                smsRepo.loadSmsThreads(currentContacts)
                            }
                        )
                    }

                    MainTab.MESSAGES -> {
                        SmsListScreen(
                            threads = smsThreads,
                            searchQuery = searchQuery,
                            onThreadClick = { thread -> activeChatThreadId = thread.id },
                            onNewMessageClick = { showNewMessageDialog = true },
                            spamThreads = spamThreads,
                            onUnblockSpamThread = { threadId -> smsRepo.unblockSpamThread(threadId) },
                            onBlockThread = { threadId -> smsRepo.blockThread(threadId) },
                            onDeleteThread = { threadId -> smsRepo.deleteThread(threadId) }
                        )
                    }
                }
            }
        }

        // Active Chat Screen Overlay (Placed AFTER Scaffold so it renders ON TOP)
        val activeChat = remember(effectiveActiveChatThreadId, smsThreads, contacts) {
            val targetId = effectiveActiveChatThreadId ?: return@remember null
            val targetClean = ContactMerger.normalizePhoneNumber(targetId)
            val foundThread = smsThreads.find { thread ->
                thread.id == targetId ||
                        (targetClean.isNotBlank() && ContactMerger.normalizePhoneNumber(thread.contactNumber) == targetClean) ||
                        thread.contactName.equals(targetId, ignoreCase = true)
            }
            foundThread ?: run {
                val matchedContact = ContactMerger.findContactForNumber(contacts, targetId)
                val displayName = matchedContact?.name ?: targetId
                val realNum = matchedContact?.phoneNumber ?: targetId
                SmsThread(
                    id = targetId,
                    contactName = displayName,
                    contactNumber = realNum,
                    lastMessage = "",
                    timestamp = "Now",
                    lastTimestampMillis = System.currentTimeMillis(),
                    unreadCount = 0,
                    avatarColorIndex = 0,
                    messages = emptyList<SmsMessage>()
                )
            }
        }
        // Synchronize Active Chat Number for precise notification suppression
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, activeChatThreadId, activeChat?.contactNumber) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                        if (activeChatThreadId != null && activeChat != null) {
                            DynamicIslandNotificationHelper.activeChatNumber = activeChat.contactNumber
                        } else {
                            DynamicIslandNotificationHelper.activeChatNumber = null
                        }
                    }
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        DynamicIslandNotificationHelper.activeChatNumber = null
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            if (activeChatThreadId != null && activeChat != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                DynamicIslandNotificationHelper.activeChatNumber = activeChat.contactNumber
            } else {
                DynamicIslandNotificationHelper.activeChatNumber = null
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                DynamicIslandNotificationHelper.activeChatNumber = null
            }
        }

        if (activeChat != null) {
            LaunchedEffect(activeChat.id) {
                smsRepo.markThreadAsRead(activeChat.id)
            }
            val isSaved = ContactMerger.findContactForNumber(contacts, activeChat.contactNumber) != null
            ChatScreen(
                thread = activeChat,
                callLogs = callLogs,
                allThreads = smsThreads,
                isSavedContact = isSaved,
                onBackClick = { activeChatThreadId = null },
                onSelectThread = { selectedThread -> activeChatThreadId = selectedThread.id },
                onCallClick = {
                    onPerformCall(activeChat.contactName, activeChat.contactNumber, null) { title, reason ->
                        islandState = DynamicIslandState.SpamBlockedAlert(title, reason)
                    }
                    if (!CountryCodeFilter.isFromOutsideTurkey(activeChat.contactNumber) && !TurkeySpamNumberBlocker.isTurkishSpamNumber(activeChat.contactNumber)) {
                        activeCallData = Pair(activeChat.contactName, activeChat.contactNumber)
                        isOutgoingCall = true
                        isCallAnswered = false
                        isCallMinimized = false
                    }
                },
                onSaveContactClick = {
                    initialAddContactNumber = activeChat.contactNumber
                    showAddContactDialog = true
                },
                onDeleteMessage = { msg -> smsRepo.deleteSingleMessage(activeChat.id, msg) },
                onSendMessage = { text, subId ->
                    val status = smsRepo.sendMessage(activeChat.id, text, subId, contacts)
                    when (status) {
                        is SendStatus.RateLimited -> {
                            islandState = DynamicIslandState.SpamBlockedAlert("Rate Limited", status.message)
                        }
                        is SendStatus.DuplicateBlocked -> {
                            islandState = DynamicIslandState.SpamBlockedAlert("Duplicate Blocked", status.message)
                        }
                        is SendStatus.BrandBlocked -> {
                            islandState = DynamicIslandState.SpamBlockedAlert("Reply Not Allowed", status.message)
                        }
                        else -> {}
                    }
                    status
                }
            )
        }

        // Top Floating Dynamic Island Overlay Notification
        DynamicIslandNotification(
            state = islandState,
            onExpandCall = {
                if (activeCallData != null) {
                    isCallMinimized = false
                }
            },
            onDismiss = {
                if (activeCallData != null) {
                    isCallMinimized = false
                } else {
                    islandState = DynamicIslandState.Idle
                }
            },
            onOpenSms = {
                val currentAlert = islandState
                if (currentAlert is DynamicIslandState.SmsAlert) {
                    currentTab = MainTab.MESSAGES
                    val sender = currentAlert.senderNumber.ifBlank { currentAlert.senderName }
                    val existingThread = smsThreads.find {
                        it.contactNumber == sender || it.contactName == sender ||
                        ContactMerger.normalizePhoneNumber(it.contactNumber) == ContactMerger.normalizePhoneNumber(sender)
                    }
                    if (existingThread != null) {
                        activeChatThreadId = existingThread.id
                    } else {
                        val (_, newId) = smsRepo.createThread(sender, sender)
                        if (newId != null) {
                            activeChatThreadId = newId
                        }
                    }
                    islandState = DynamicIslandState.Idle
                }
            },
            onQuickReply = { number, message ->
                smsRepo.sendMessage(number, message)
                islandState = DynamicIslandState.Idle
                try {
                    val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notifManager?.cancelAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onMarkAsRead = { threadId ->
                smsRepo.markThreadAsRead(threadId)
                islandState = DynamicIslandState.Idle
            },
            onEndCall = {
                TelecomCallManager.endOrDeclineCall(context)
                activeCallData = null
                islandState = DynamicIslandState.Idle
            }
        )
    }

    // Contact Detail Bottom Sheet with Real-Time Contact State Resolution
    val rawDetailContact = selectedContactForDetail
    val detailContact = remember(rawDetailContact?.id, contacts) {
        if (rawDetailContact == null) null
        else contacts.find { it.id == rawDetailContact.id || ContactMerger.normalizePhoneNumber(it.phoneNumber) == ContactMerger.normalizePhoneNumber(rawDetailContact.phoneNumber) } ?: rawDetailContact
    }
    if (detailContact != null) {
        ContactDetailSheet(
            contact = detailContact,
            onDismiss = { selectedContactForDetail = null },
            onCallClick = {
                onPerformCall(detailContact.name, detailContact.phoneNumber, null) { title, reason ->
                    islandState = DynamicIslandState.SpamBlockedAlert(title, reason)
                }
                if (!CountryCodeFilter.isFromOutsideTurkey(detailContact.phoneNumber) && !TurkeySpamNumberBlocker.isTurkishSpamNumber(detailContact.phoneNumber)) {
                    activeCallData = Pair(detailContact.name, detailContact.phoneNumber)
                    isOutgoingCall = true
                    isCallAnswered = false
                    isCallMinimized = false
                }
            },
            onSmsClick = {
                val existingThread = smsThreads.find {
                    it.contactNumber == detailContact.phoneNumber || it.contactName == detailContact.name
                }
                if (existingThread != null) {
                    activeChatThreadId = existingThread.id
                } else {
                    val (_, newId) = smsRepo.createThread(detailContact.name, detailContact.phoneNumber)
                    if (newId != null) {
                        activeChatThreadId = newId
                    }
                }
            },
            onEditClick = {
                val currentContacts = contactsRepo.contacts.value
                val fullContact = currentContacts.find { it.id == detailContact.id } ?: detailContact
                contactToEditFromDetail = fullContact
            }
        )
    }

    // Edit Contact Dialog from Contact Detail Sheet
    val targetEditDetail = contactToEditFromDetail
    if (targetEditDetail != null) {
        EditContactDialog(
            contact = targetEditDetail,
            onDismiss = { contactToEditFromDetail = null },
            onSave = { name, number, photoUri, note ->
                contactsRepo.updateContact(targetEditDetail.id, name, number, photoUri, note)
                val currentContacts = contactsRepo.contacts.value
                callLogRepo.loadCallLogs(currentContacts)
                smsRepo.loadSmsThreads(currentContacts)
                contactToEditFromDetail = null
            },
            onDelete = {
                contactsRepo.deleteContact(targetEditDetail.id, targetEditDetail.phoneNumber)
                val currentContacts = contactsRepo.contacts.value
                callLogRepo.loadCallLogs(currentContacts)
                smsRepo.loadSmsThreads(currentContacts)
                contactToEditFromDetail = null
            }
        )
    }

    // Add Contact Dialog
    if (showAddContactDialog) {
        AddContactDialog(
            initialNumber = initialAddContactNumber,
            onDismiss = {
                showAddContactDialog = false
                initialAddContactNumber = ""
            },
            onSave = { name, number, photoUri, note ->
                contactsRepo.addContact(name, number, photoUri, note)
                val currentContacts = contactsRepo.contacts.value
                callLogRepo.loadCallLogs(currentContacts)
                smsRepo.loadSmsThreads(currentContacts)
                showAddContactDialog = false
                initialAddContactNumber = ""
                activeChatThreadId = null
                currentTab = MainTab.CONTACTS
            }
        )
    }

    // New Message Full-Screen View
    if (showNewMessageDialog) {
        NewMessageDialog(
            contacts = contacts,
            onDismiss = { showNewMessageDialog = false },
            onOpenChat = { name, number ->
                val (_, newId) = smsRepo.createThread(name, number)
                showNewMessageDialog = false
                if (newId != null) {
                    activeChatThreadId = newId
                }
            }
        )
    }
}
