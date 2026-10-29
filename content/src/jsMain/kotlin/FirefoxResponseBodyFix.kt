import common.isFirefox

/**
 * В Firefox content script тело fetch-ответа приходит из чужого compartment (Xray):
 * чанк не проходит `instanceof Uint8Array`, а созданный поверх его буфера Int8Array
 * не даёт обратиться к `constructor` ("Permission denied to access property").
 *
 * Ktor JS-движок читает тело именно так (`asByteArray` -> `Int8Array(chunk.buffer, ...)`),
 * поэтому запросы с непустым телом в content script никогда не завершаются.
 *
 * Копируем каждый чанк в нативный Uint8Array content script'а до того, как его получит Ktor.
 * В Chrome чанки уже нативные, поэтому там шим ничего не делает.
 */
@Suppress("UnsafeCastFromDynamic")
fun installFirefoxResponseBodyFix() {
    if (!isFirefox()) return
    js(
        """
        (function () {
            if (typeof ReadableStreamDefaultReader === "undefined") return;
            var proto = ReadableStreamDefaultReader.prototype;
            if (proto.__prozenNativeChunks) return;
            proto.__prozenNativeChunks = true;
            var originalRead = proto.read;
            proto.read = function () {
                return originalRead.call(this).then(function (result) {
                    if (!result || result.done) return result;
                    var chunk = result.value;
                    if (chunk instanceof Uint8Array) return result;
                    var copy = new Uint8Array(chunk.length);
                    for (var i = 0; i < chunk.length; i++) copy[i] = chunk[i] & 0xff;
                    return { value: copy, done: false };
                });
            };
        })();
        """
    )
}
