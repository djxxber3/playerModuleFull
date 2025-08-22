package com.footstique.player.ui.urlinput

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // <-- تم تغيير الـ import
import com.footstique.player.feature.player.PlayerActivity

@Composable
fun UrlInputScreen(
    modifier: Modifier = Modifier,
    viewModel: UrlInputViewModel = viewModel() // <-- تم تغيير hiltViewModel() إلى viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Video URL", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.videoUrl,
            onValueChange = viewModel::onVideoUrlChange,
            label = { Text("Video URL") },
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.isUrlEmptyError,
            singleLine = true
        )
        if (uiState.isUrlEmptyError) {
            Text(
                "URL cannot be empty",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (viewModel.onPlayVideoClick()) {
                    launchPlayerActivity(context, uiState.videoUrl)
                } else {
                    Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Play Video")
        }
    }
}

private fun launchPlayerActivity(context: Context, videoUrl: String) {
    // الطريقة الصحيحة لتمرير الرابط إلى المشغل
    val intent = Intent(context, PlayerActivity::class.java).apply {
        data = Uri.parse(videoUrl)
    }
    context.startActivity(intent)
}