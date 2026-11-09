package com.dietpizza.rxplayer.feature.onboarding

import android.content.Context
import android.os.Environment
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** STUB — replaced by the onboarding agent. Keep both signatures stable. */
fun hasRequiredPermissions(context: Context): Boolean = Environment.isExternalStorageManager()

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onFinished, modifier = modifier) { Text("Continue") }
}
