version = "1.0.0"
description = "Customize Discord presence indicator colors independently."

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Initial release.
        * Configure online, idle, do not disturb, and offline/invisible colors separately.
        * Persist color choices in Aliucord plugin settings.
        """.trimIndent(),
    )
    deploy.set(false)
}
