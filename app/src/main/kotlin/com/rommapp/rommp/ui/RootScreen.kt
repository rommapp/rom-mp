package com.rommapp.rommp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rommapp.rommp.ui.auth.AuthScreen
import com.rommapp.rommp.ui.input.CursorHost

@Composable
fun RootScreen(viewModel: RootViewModel = hiltViewModel()) {
    val authed by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    CursorHost {
        when (authed) {
            null -> LoadingScreen()
            false -> AuthScreen()
            true -> AppNav()
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
