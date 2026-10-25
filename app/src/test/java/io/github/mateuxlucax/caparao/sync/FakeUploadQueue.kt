package io.github.mateuxlucax.caparao.sync

/** Records what would go to WorkManager. */
class FakeUploadQueue : UploadQueue {
    val calls = mutableListOf<String>()

    override fun enqueue(brewId: String, replace: Boolean) {
        calls += if (replace) "replace $brewId" else "keep $brewId"
    }

    override fun cancel(brewId: String) {
        calls += "cancel $brewId"
    }

    override fun cancelAll() {
        calls += "cancel all"
    }
}
