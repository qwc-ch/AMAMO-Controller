package com.gree.remote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 模式：0 自动 / 1 制冷 / 2 除湿 / 3 送风 / 4 制热 */
private val modes = listOf(0 to "自动", 1 to "制冷", 2 to "除湿", 3 to "送风", 4 to "制热")

@Composable
fun RemoteScreen(vm: RemoteViewModel) {
    val s by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("遥控器", style = MaterialTheme.typography.headlineMedium)

        if (s.connecting) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
            Text("正在连接局域网空调…")
        } else if (!s.connected) {
            // 未连接：手动 IP + 重连
            Spacer(Modifier.height(16.dp))
            Text("未连接。请确认手机与空调在同一 WiFi。", color = MaterialTheme.colorScheme.error)
            s.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = { vm.connect(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("重试")
            }
        } else {
            val st = s.state

            // 开关机
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("电源", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = st.power == 1,
                    onCheckedChange = { vm.send("Pow", if (it) 1 else 0) },
                )
            }

            // 模式按钮组
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("模式", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        modes.forEach { (v, label) ->
                            OutlinedButton(
                                onClick = { vm.send("Mod", v) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // 温度
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("设定温度", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${st.decodedSetTemp()}℃",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Row {
                        OutlinedButton(onClick = { vm.send("SetTem", st.decodedSetTemp() - 1) }) {
                            Text("−")
                        }
                        Spacer(Modifier.width(24.dp))
                        OutlinedButton(onClick = { vm.send("SetTem", st.decodedSetTemp() + 1) }) {
                            Text("+")
                        }
                    }
                    st.roomTemp.takeIf { it != 0 }?.let {
                        Text("室温 ${it}℃", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 风速
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("风速", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to "自动", 1 to "低", 2 to "中", 3 to "高").forEach { (v, label) ->
                            OutlinedButton(onClick = { vm.send("WdSpd", v) }) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // 辅助功能
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.send("Tur", if (st.turbo == 1) 0 else 1) }) {
                    Text(if (st.turbo == 1) "强劲 ●" else "强劲")
                }
                OutlinedButton(onClick = { vm.send("Quiet", if (st.quiet == 1) 0 else 1) }) {
                    Text(if (st.quiet == 1) "静音 ●" else "静音")
                }
                OutlinedButton(onClick = { vm.send("SwUpDn", if (st.swingUpDown != 0) 0 else 1) }) {
                    Text(if (st.swingUpDown != 0) "扫风 ●" else "扫风")
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.connect(null) }) { Text("重连") }
        }
    }
}