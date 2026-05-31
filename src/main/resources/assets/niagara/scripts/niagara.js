/**
 * Niagara JS bridge — include this in your HTML:
 *   <script src="/assets/niagara/ultralight/niagara.js"></script>
 *
 * Works in-game (Ultralight) and in a browser (stub mode for development).
 */
(function () {
    var _listeners = {};

    function _dispatch(event, data) {
        var fns = _listeners[event];
        if (!fns) return;
        for (var i = 0; i < fns.length; i++) {
            try { fns[i](data); } catch (e) { console.error('[Niagara] Listener error:', e); }
        }
    }

    function on(event, fn) {
        if (!_listeners[event]) _listeners[event] = [];
        _listeners[event].push(fn);
    }

    var emit;

    if (window.__NIAGARA_RUNTIME__) {
        // In-game: route through Java-backed _javaEmit function.
        // _javaEmit is installed by EventBridge.installIntoContext() before this script runs.
        emit = function (event, data) {
            if (window.niagara && window.niagara._javaEmit) {
                window.niagara._javaEmit(event, JSON.stringify(data || {}));
            }
        };
    } else {
        // Browser stub: log and echo back for self-contained UI testing.
        emit = function (event, data) {
            console.log('[Niagara stub] emit:', event, data);
            // Echo back so JS-only test harnesses can simulate round-trips.
            setTimeout(function () { _dispatch(event, data); }, 0);
        };
    }

    // Extend window.niagara (may already have _javaEmit set by Java).
    window.niagara = window.niagara || {};
    window.niagara.on = on;
    window.niagara.emit = emit;
    window.niagara._dispatch = _dispatch;
    window.niagara._listeners = _listeners;
}());