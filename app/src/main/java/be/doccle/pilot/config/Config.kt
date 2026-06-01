package be.doccle.pilot.config

object Config {
    const val NTFY_BASE: String = "https://ntfy.sh"

    // MUST match Copilot's Config.NTFY_TOPIC exactly. Generate once with:
    //   echo "copilot-$(openssl rand -hex 16)"
    // then paste the same value here and in Copilot/.../config/Config.kt.
    const val NTFY_TOPIC: String = "REPLACE_WITH_YOUR_NTFY_TOPIC"
}
