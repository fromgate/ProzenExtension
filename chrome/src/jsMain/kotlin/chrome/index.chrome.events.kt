@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS", "EXTERNAL_DELEGATION", "NESTED_CLASS_IN_EXTERNAL_INTERFACE")
@file:JsQualifier("chrome.events")
package chrome.events

import kotlin.js.*
import kotlin.reflect.KFunction1

external interface UrlFilter {
    var schemes: Array<String>? get() = definedExternally; set(value) = definedExternally
    var urlMatches: String? get() = definedExternally; set(value) = definedExternally
    var pathContains: String? get() = definedExternally; set(value) = definedExternally
    var hostSuffix: String? get() = definedExternally; set(value) = definedExternally
    var hostPrefix: String? get() = definedExternally; set(value) = definedExternally
    var hostContains: String? get() = definedExternally; set(value) = definedExternally
    var urlContains: String? get() = definedExternally; set(value) = definedExternally
    var querySuffix: String? get() = definedExternally; set(value) = definedExternally
    var urlPrefix: String? get() = definedExternally; set(value) = definedExternally
    var hostEquals: String? get() = definedExternally; set(value) = definedExternally
    var urlEquals: String? get() = definedExternally; set(value) = definedExternally
    var queryContains: String? get() = definedExternally; set(value) = definedExternally
    var pathPrefix: String? get() = definedExternally; set(value) = definedExternally
    var pathEquals: String? get() = definedExternally; set(value) = definedExternally
    var pathSuffix: String? get() = definedExternally; set(value) = definedExternally
    var queryEquals: String? get() = definedExternally; set(value) = definedExternally
    var queryPrefix: String? get() = definedExternally; set(value) = definedExternally
    var urlSuffix: String? get() = definedExternally; set(value) = definedExternally
    var ports: Array<Any>? get() = definedExternally; set(value) = definedExternally
    var originAndPathMatches: String? get() = definedExternally; set(value) = definedExternally
}
external interface Event<T : Function<*>> {
    fun addListener(callback: T)
    fun getRules(callback: (rules: Array<Rule>) -> Unit)
    fun getRules(ruleIdentifiers: Array<String>, callback: (rules: Array<Rule>) -> Unit)
    fun hasListener(callback: T): Boolean
    fun removeRules(ruleIdentifiers: Array<String>? = definedExternally /* null */, callback: (() -> Unit)? = definedExternally /* null */)
    fun removeRules(callback: (() -> Unit)? = definedExternally /* null */)
    fun addRules(rules: Array<Rule>, callback: ((rules: Array<Rule>) -> Unit)? = definedExternally /* null */)
    fun removeListener(callback: T)
    fun hasListeners(): Boolean
    fun removeRules()
}
external interface Rule {
    var priority: Number? get() = definedExternally; set(value) = definedExternally
    var conditions: Array<Any>
    var id: String? get() = definedExternally; set(value) = definedExternally
    var actions: Array<Any>
    var tags: Array<String>? get() = definedExternally; set(value) = definedExternally
}
