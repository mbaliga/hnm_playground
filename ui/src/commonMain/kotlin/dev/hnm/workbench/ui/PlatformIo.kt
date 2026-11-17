package dev.hnm.workbench.ui

/**
 * Host file and clipboard access, in the style of `PatternPlayer.None`. A host that can open, save or
 * copy supplies an implementation; everywhere else [None] is used and the editor shows no such controls
 * (export stays on-screen text).
 */
interface PlatformIo {
    /** Ask the user for a text file; [onResult] gets its content, or null if cancelled or unreadable. */
    fun openText(onResult: (String?) -> Unit)

    /** Offer [text] for saving under [suggestedName]. */
    fun saveText(suggestedName: String, text: String)

    /** Put [text] on the system clipboard. */
    fun copyText(text: String)

    /** The default: no host I/O. Identity-compared, like `PatternPlayer.None`. */
    object None : PlatformIo {
        override fun openText(onResult: (String?) -> Unit) = onResult(null)
        override fun saveText(suggestedName: String, text: String) = Unit
        override fun copyText(text: String) = Unit
    }
}
