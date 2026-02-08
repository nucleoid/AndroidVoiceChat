package ai.openclaw.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.openclaw.android.gateway.GatewaySession

@Composable
fun StatusPill(
    connectionState: GatewaySession.ConnectionState,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (connectionState) {
        is GatewaySession.ConnectionState.Disconnected -> "Disconnected" to Color(0xFFFF6B6B)
        is GatewaySession.ConnectionState.Connecting -> "Connecting..." to Color(0xFFFFBB33)
        is GatewaySession.ConnectionState.WaitingForPairing -> "Pairing..." to Color(0xFFFFBB33)
        is GatewaySession.ConnectionState.Connected -> "Connected" to Color(0xFF4CAF50)
        is GatewaySession.ConnectionState.Error -> "Error" to Color(0xFFFF6B6B)
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "statusColor")

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(animatedColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
