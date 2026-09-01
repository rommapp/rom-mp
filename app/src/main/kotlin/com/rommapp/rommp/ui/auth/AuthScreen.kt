package com.rommapp.rommp.ui.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rommapp.rommp.R
import com.rommapp.rommp.data.auth.PairingChallenge
import com.rommapp.rommp.ui.input.FocusHalo
import com.rommapp.rommp.ui.theme.FadedInk
import com.rommapp.rommp.ui.theme.FadedRule

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (val phase = state.phase) {
            AuthPhase.EnterServer -> ServerEntry(
                serverUrl = state.serverUrl,
                onChange = viewModel::onServerUrlChange,
                onSettled = viewModel::onServerUrlSettled,
                onConnect = viewModel::startPairing,
            )

            AuthPhase.Connecting -> ConnectingIndicator()

            is AuthPhase.AwaitingApproval -> PairingPanel(phase.challenge)

            is AuthPhase.Error -> ErrorPanel(phase.message, viewModel::cancel)
        }
    }
}

@Composable
private fun ServerEntry(
    serverUrl: String,
    onChange: (String) -> Unit,
    onSettled: () -> Unit,
    onConnect: () -> Unit,
) {
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onChange,
        label = { Text("Server address") },
        placeholder = { Text("romm.example.com") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onSettled() },
    )
    Spacer(Modifier.height(16.dp))
    FocusHalo(onActivate = onConnect, modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Text("Connect")
        }
    }
}

@Composable
private fun ConnectingIndicator() {
    CircularProgressIndicator()
    Spacer(Modifier.height(12.dp))
    Text("Contacting server...")
}

/**
 * Approval has two routes: scan the QR from a phone, or open the same
 * verification URL in this device's browser when the user can sign in here.
 * Either way the caller keeps polling, so approval lands without coming back.
 */
@Composable
private fun PairingPanel(challenge: PairingChallenge) {
    val qr = remember(challenge.qrContent) { QrEncoder.encode(challenge.qrContent) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var noBrowser by remember(challenge.verificationUrl) { mutableStateOf(false) }

    LaunchedEffect(challenge.verificationUrl) { focusRequester.requestFocus() }

    Image(
        bitmap = qr,
        contentDescription = "Pairing QR code",
        modifier = Modifier.size(240.dp),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        "Scan with your phone to register this device",
        style = MaterialTheme.typography.bodySmall,
        color = FadedInk,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    OrDivider()
    Spacer(Modifier.height(16.dp))

    val openInBrowser = {
        noBrowser = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, challenge.verificationUrl.toUri()))
            false
        } catch (_: ActivityNotFoundException) {
            true
        }
    }
    FocusHalo(onActivate = openInBrowser, modifier = Modifier.focusRequester(focusRequester)) {
        Button(onClick = openInBrowser) { Text("Sign in on this device") }
    }

    if (noBrowser) {
        Spacer(Modifier.height(12.dp))
        Text(
            "No browser on this device. Scan the code instead.",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = FadedRule)
        Text(
            "or",
            style = MaterialTheme.typography.bodySmall,
            color = FadedInk,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = FadedRule)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    FocusHalo(onActivate = onRetry) {
        Button(onClick = onRetry) { Text("Try again") }
    }
}
