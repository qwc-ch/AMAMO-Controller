package com.gree.remote.ui.remote

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gree.remote.data.model.AcState
import com.gree.remote.ui.theme.ExpressiveMotion
import com.gree.remote.ui.theme.PillShape
import java.util.Locale

/** 模式：0 自动 / 1 制冷 / 2 除湿 / 3 送风 / 4 制热 */
private val modes = listOf(0 to "自动", 1 to "制冷", 2 to "除湿", 3 to "送风", 4 to "制热")

/** 风速（标准表）：0 自动，1 低，3 中，5 高（2/4 仅五档机型，三档机型无效）。 */
private val fanSpeeds = listOf(0 to "自动", 1 to "低风", 3 to "中风", 5 to "高风")

/** 上下风向档位（标准表）：0 默认合拢，1 全程扫风，2~6 固定角度最上→最下。 */
private val swingPositions = listOf(
    0 to "合拢", 1 to "扫风", 2 to "最上", 3 to "中上", 4 to "居中", 5 to "中下", 6 to "最下",
)

/**
 * 单台设备遥控面板（嵌在首页设备分页内）。
 * 连接由 [RemoteViewModel] 自管：进入即连接并轮询，切走分页自动暂停。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemotePanel(vm: RemoteViewModel, deviceName: String, deviceIp: String) {
    val s by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- 头部：名称 + IP + 状态 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(deviceName, style = MaterialTheme.typography.titleMedium)
                Text(
                    deviceIp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                s.connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.padding(2.dp))
                    Text("连接中", style = MaterialTheme.typography.labelMedium)
                }
                s.connected -> Text(
                    "已连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
                    "未连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        s.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        when {
            s.connecting -> ConnectingPlaceholder()
            !s.connected -> {
                Button(
                    onClick = vm::reconnect,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("重试连接") }
            }
            else -> ConnectedControls(vm, s)
        }
    }
}

@Composable
private fun ConnectingPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            "正在连接局域网空调…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 已连接：电源 + 温度 + 模式 + 风速 + 功能开关。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedControls(vm: RemoteViewModel, s: RemoteUiState) {
    val st = s.state
    val powered = st.power == 1

    // ---- 电源 hero 卡 ----
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (powered) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (powered) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (powered) "运行中" else "已关机",
                    style = MaterialTheme.typography.titleLarge,
                )
                val modeLabel = modes.firstOrNull { it.first == st.mode }?.second ?: "自动"
                val fanLabel = fanSpeeds.firstOrNull { it.first == st.windSpeed }?.second ?: "档位${st.windSpeed}"
                Text(
                    if (powered) "$modeLabel · 风速 $fanLabel"
                    else "打开开关启动空调",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = powered,
                onCheckedChange = {
                    // 开机走全量下发（遥控器式，导风板位置正常）；关机单字段即可
                    if (it) vm.powerOn() else vm.send("Pow", 0)
                },
            )
        }
    }

    if (!powered) {
        Text(
            "关机状态下仅可开关电源",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    TempControlCard(vm, st, busy = s.busy)

    // ---- 模式 ----
    Text("模式", style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = st.mode == value,
                onClick = { vm.send("Mod", value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                enabled = !s.busy,
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }

    // ---- 风速 ----
    Text("风速", style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        fanSpeeds.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = st.windSpeed == value,
                onClick = { vm.send("WdSpd", value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = fanSpeeds.size),
                enabled = !s.busy,
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }

    // ---- 上下风向（SwUpDn 档位单选，标准表 0~6；真机实测 6 = 最下）----
    Text("上下风向", style = MaterialTheme.typography.titleSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        swingPositions.forEach { (value, label) ->
            FilterChip(
                selected = st.swingUpDown == value,
                onClick = { vm.send("SwUpDn", value) },
                enabled = !s.busy,
                label = { Text(label) },
            )
        }
    }

    // ---- 功能开关（FilterChip 组） ----
    // 注：SwUpDn/SwingLfRig 仅 1 表示全程扫风，2~6 是固定角度档（标准表），
    // 故"左右扫风"以 == 1 判断；上下风向已用档位单选呈现，不放开关。
    val toggles = listOf(
        ToggleDef("强劲", st.turbo == 1) { vm.send("Tur", if (st.turbo == 1) 0 else 1) },
        ToggleDef("静音", st.quiet == 1) { vm.send("Quiet", if (st.quiet == 1) 0 else 1) },
        ToggleDef("左右扫风", st.swingLeftRight == 1) { vm.send("SwingLfRig", if (st.swingLeftRight == 1) 0 else 1) },
        ToggleDef("电辅热", st.heatAux == 1) { vm.send("StHt", if (st.heatAux == 1) 0 else 1) },
        ToggleDef("干燥防霉", st.blowDry == 1) { vm.send("Blo", if (st.blowDry == 1) 0 else 1) },
        ToggleDef("负离子", st.health == 1) { vm.send("Health", if (st.health == 1) 0 else 1) },
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toggles.forEach { t ->
            FilterChip(
                selected = t.checked,
                onClick = t.onToggle,
                enabled = !s.busy,
                label = { Text(t.label) },
            )
        }
    }
}

/** 功能开关定义。 */
private data class ToggleDef(
    val label: String,
    val checked: Boolean,
    val onToggle: () -> Unit,
)

/**
 * 温度控制卡：大数字弹跳动效（MD3E 时刻）+ ± 步进。
 * 支持 TemRec 半度的设备按 0.5℃ 步进，否则 1℃。
 */
@Composable
private fun TempControlCard(vm: RemoteViewModel, st: AcState, busy: Boolean) {
    val step = if (st.supportsHalfDegree()) 0.5 else 1.0
    val current = st.decodedSetTemp()
    val tempText = String.format(Locale.US, "%.1f", current).removeSuffix(".0")

    // 温度变化时的弹跳强调
    val scale = remember { Animatable(1f) }
    LaunchedEffect(tempText) {
        scale.snapTo(0.9f)
        scale.animateTo(1f, ExpressiveMotion.BouncySpatial)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("设定温度", style = MaterialTheme.typography.titleSmall)
            Text(
                "$tempText℃",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            )
            st.decodedRoomTemp()?.let {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("室温 $it℃") },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { vm.setTemp(current - step) },
                    enabled = !busy && current > RemoteViewModel.MIN_TEMP,
                    shape = PillShape,
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                FilledTonalButton(
                    onClick = { vm.setTemp(current + step) },
                    enabled = !busy && current < RemoteViewModel.MAX_TEMP,
                    shape = PillShape,
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            if (st.supportsHalfDegree()) {
                Text(
                    "支持 0.5℃ 步进",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
