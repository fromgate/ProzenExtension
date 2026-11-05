// Поддерживаемые браузеры (Firefox 126+, Chrome/Edge) знают globalThis.
// Сообщаем об этом webpack, чтобы его runtime использовал globalThis напрямую
// и не генерировал запасной `new Function("return this")`, на который ругается AMO.
config.output = config.output || {};
config.output.environment = Object.assign({}, config.output.environment, { globalThis: true });
