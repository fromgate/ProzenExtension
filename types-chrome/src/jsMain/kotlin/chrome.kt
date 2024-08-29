package chrome

external object chrome {
    val runtime: Runtime
    val storage: Storage
    val i18n: I18n

    interface Runtime {
        fun getManifest(): Manifest
    }

    interface Storage {
        val local: LocalStorage
    }

    interface LocalStorage {
        fun get(keys: Array<String>, callback: (dynamic) -> Unit)
        fun set(items: dynamic)
    }

    interface Manifest {
        val version: String
    }

    interface I18n {
        fun getMessage(key: String): String?
    }
}