package com.gree.remote.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DevicesScreen(vm: DevicesViewModel, onSelect: (String) -> Unit) {
    val s by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.loadCloudDevices() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("设备列表", style = MaterialTheme.typography.headlineMedium)

        s.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (s.loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn {
                items(s.devices, key = { it.mac }) { dev ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onSelect(dev.mac) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(dev.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (dev.online) "在线 · ${dev.mac}" else "离线 · ${dev.mac}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.loadCloudDevices() }, modifier = Modifier.fillMaxWidth()) {
                Text("刷新")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "提示：若在同一个 WiFi 下，也可直接进遥控页走局域网直连（延迟低）。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}