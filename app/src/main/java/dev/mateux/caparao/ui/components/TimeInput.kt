package dev.mateux.caparao.ui.components

/** Parses seconds or m:ss, up to one hour. */
fun parseClockText(text: String): Long? {
    val parts = text.trim().split(':')
    val seconds = when (parts.size) {
        1 -> parts[0].toLongOrNull()
        2 -> parts[0].toLongOrNull()?.let { minutes ->
            parts[1].takeIf { it.length == 2 }?.toLongOrNull()?.takeIf { it < 60 }?.let { minutes * 60 + it }
        }
        else -> null
    }
    return seconds?.takeIf { it in 0..3600 }?.times(1000)
}

fun formatClockText(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
