// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

/**
 * Made-up apps for the demo build: 30 user and 10 system apps with different names (A to Z, so the
 * letter popup has plenty to show), install dates spread over about three years and sizes from 1 MB
 * to 390 MB. Nothing here is real.
 */
object DemoData {
    private const val DAY = 24L * 60 * 60 * 1000

    private class Spec(val name: String, val daysAgo: Int, val mb: Double, val system: Boolean)

    private fun user(name: String, daysAgo: Int, mb: Double) = Spec(name, daysAgo, mb, false)
    private fun system(name: String, daysAgo: Int, mb: Double) = Spec(name, daysAgo, mb, true)

    private val specs = listOf(
        user("Aurora Notes", 3, 18.4),
        user("Bluefin Browser", 9, 96.2),
        user("Cascade Music", 15, 64.8),
        user("Dune Maps", 22, 142.0),
        user("Ember Camera", 35, 52.1),
        user("Fable Reader", 48, 23.7),
        user("Glimmer Photos", 61, 118.5),
        user("Harbor Mail", 74, 31.3),
        user("Ion Fitness", 90, 77.9),
        user("Juniper Calendar", 110, 12.6),
        user("Kite Weather", 133, 9.8),
        user("Lumen Flashlight", 158, 1.4),
        user("Mosaic Wallet", 187, 45.0),
        user("Nimbus Cloud", 214, 203.7),
        user("Orbit Podcasts", 246, 38.2),
        user("Pixel Pond", 281, 310.5),
        user("Quartz Clock", 320, 2.9),
        user("Rally Racing", 355, 389.6),
        user("Saffron Recipes", 402, 27.4),
        user("Tidal Radio", 447, 41.0),
        user("Umber Sketch", 495, 55.6),
        user("Velvet Chat", 540, 88.3),
        user("Willow Journal", 598, 6.2),
        user("Xylo Keyboard", 640, 19.1),
        user("Yarrow Habits", 701, 8.4),
        user("Zephyr VPN", 760, 34.7),
        user("Atlas Translator", 820, 72.5),
        user("Breeze Scanner", 905, 15.3),
        user("Comet Puzzles", 980, 126.8),
        user("Delta Files", 1066, 4.7),
        system("Core Services", 800, 58.2),
        system("Device Health", 812, 21.6),
        system("Setup Wizard", 822, 9.3),
        system("Carrier Services", 835, 33.8),
        system("Emergency Info", 870, 3.1),
        system("Print Spooler", 907, 5.7),
        system("Backup Transport", 940, 2.2),
        system("Live Wallpapers", 983, 14.9),
        system("Bluetooth Share", 1010, 4.4),
        system("Wallpaper Picker", 1040, 11.8)
    )

    fun packageName(name: String) = "demo." + name.lowercase().replace(' ', '.')

    private val names = specs.associate { packageName(it.name) to it.name }

    fun nameOf(packageName: String): String = names[packageName] ?: packageName

    fun apps(now: Long = System.currentTimeMillis()): List<AppInfo> = specs.map {
        AppInfo(packageName(it.name), it.name, (it.mb * 1024 * 1024).toLong(), now - it.daysAgo * DAY, it.system)
    }
}
