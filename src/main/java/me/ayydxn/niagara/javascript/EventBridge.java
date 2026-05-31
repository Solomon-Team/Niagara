package me.ayydxn.niagara.javascript;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import me.ayydxn.luminescence.javascript.JSContext;
import me.ayydxn.luminescence.javascript.JSException;
import me.ayydxn.luminescence.javascript.JSFunction;
import me.ayydxn.luminescence.javascript.JSObject;
import me.ayydxn.luminescence.view.ULView;
import me.ayydxn.niagara.NiagaraClientMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Wires the {@code window.niagara} emit/on event bridge into a {@link ULView}'s JavaScript context.
 *
 * <h2>Java→JS</h2>
 * Call {@link #emit(String, EventData)} from any thread. The call is queued and flushed
 * on the next render frame via {@link #flushPendingEmits()}.
 *
 * <h2>JS→Java</h2>
 * JavaScript calls {@code niagara.emit(event, jsonString)} which invokes a Java-backed
 * {@link JSFunction}, dispatching to listeners registered via {@link #on(String, EventListener)}.
 *
 * @author Ayydxn
 */
public class EventBridge
{
    private final Map<String, List<EventListener>> listeners = Maps.newHashMap();
    private final ConcurrentLinkedQueue<PendingEmit> pendingEmits = Queues.newConcurrentLinkedQueue();
    private final ULView view;

    public EventBridge(ULView view)
    {
        this.view = view;
    }

    /**
     * Registers a Java listener for a named event emitted from JavaScript.
     *
     * @param event    The event name to listen for
     * @param eventListener The callback to invoke when the event fires
     */
    public void on(String event, EventListener eventListener)
    {
        this.listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(eventListener);
    }

    /**
     * Queues a Java→JS event dispatch. Thread-safe; flushed on the render thread
     * via {@link #flushPendingEmits()}.
     *
     * @param event The event name
     * @param eventData  The payload to send
     */
    public void emit(String event, EventData eventData)
    {
        this.pendingEmits.add(new PendingEmit(event, eventData.toJson()));
    }

    /**
     * Flushes all pending Java→JS emits. Must be called from the render thread
     * before compositing the view each frame.
     */
    public void flushPendingEmits()
    {
        PendingEmit pending;
        while ((pending = pendingEmits.poll()) != null)
        {
            String script = String.format(
                    "if(window.niagara&&window.niagara._dispatch)window.niagara._dispatch(%s,%s);",
                    quoteJsonString(pending.event), pending.jsonData
            );

            String[] exception = new String[1];
            view.evaluateScript(script, exception);

            if (exception[0] != null)
                NiagaraClientMod.LOGGER.warn("Niagara JS dispatch error for event '{}': {}", pending.event, exception[0]);
        }
    }

    /**
     * Installs {@code window.__NIAGARA_RUNTIME__} and the Java-backed {@code window.niagara.emit}
     * function into the view's JS context.
     *
     * <p>Must be called from a {@code ULViewListener.onDOMReady()} callback so that the
     * properties are present before any page script runs.
     */
    public void installIntoContext()
    {
        try (JSContext jsContext = view.acquireJSContextLock())
        {
            JSObject globalObject = jsContext.globalObject();

            // Signal to niagara.js that we're running inside Ultralight
            globalObject.setProperty("__NIAGARA_RUNTIME__", jsContext.make(true));

            // Create the niagara object on window
            // niagara.js (loaded by the page) sets up on() and _dispatch().
            // We only need to install the Java-backed emit() here.
            // We create a niagara object if it doesn't exist yet, or extend it.
            String[] exception = new String[1];
            view.evaluateScript("if(!window.niagara)window.niagara={};", exception);

            // Install niagara._javaEmit as the Java-backed function
            // niagara.js routes niagara.emit() to this when __NIAGARA_RUNTIME__ is true.
            JSFunction javaEmit = JSFunction.create(jsContext, "_javaEmit", (context, thisObj, args) ->
            {
                if (args.length < 2) return context.makeUndefined();
                String eventName = args[0].toString();
                String jsonPayload = args[1].toString();
                dispatchToJava(eventName, jsonPayload);
                return context.makeUndefined();
            });

            JSObject niagaraObj = (JSObject) jsContext.globalObject().getProperty("niagara");
            niagaraObj.setProperty("_javaEmit", javaEmit);

        }
        catch (JSException exception)
        {
            NiagaraClientMod.LOGGER.error("Failed to install EventBridge into JS context", exception);
        }
    }

    private void dispatchToJava(String event, String jsonPayload)
    {
        List<EventListener> eventListeners = listeners.get(event);
        if (eventListeners == null || eventListeners.isEmpty())
            return;

        EventData eventData;

        try
        {
            eventData = EventData.fromJson(jsonPayload);
        }
        catch (Exception exception)
        {
            NiagaraClientMod.LOGGER.warn("Niagara received malformed JSON payload for event '{}': {}", event, jsonPayload);
            return;
        }

        for (EventListener listener : eventListeners)
        {
            try
            {
                listener.onEvent(eventData);
            }
            catch (Exception e)
            {
                NiagaraClientMod.LOGGER.error("Exception in Niagara event listener for event '{}'", event, e);
            }
        }
    }

    private static String quoteJsonString(String s)
    {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record PendingEmit(String event, String jsonData)
    {
    }
}