package com.vladutu.pilot.config

object Config {
    const val NTFY_BASE: String = "https://ntfy.sh"

    // MUST match Copilot's Config.NTFY_TOPIC exactly. Generate once with:
    //   echo "copilot-$(openssl rand -hex 16)"
    // then paste the same value here and in Copilot/.../config/Config.kt.
    const val NTFY_TOPIC: String = "copilot-689e337645dc256a2b03d210d7b3c41b"
}
