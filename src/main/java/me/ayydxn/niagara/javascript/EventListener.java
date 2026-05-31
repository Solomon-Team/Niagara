package me.ayydxn.niagara.javascript;

/**
 * Callback for events received from a Niagara view's JavaScript context.
 *
 * @author Ayydxn
 */
public interface EventListener
{
    /**
     * Called when a JS {@code niagara.emit(event, data)} fires on the matching event name.
     *
     * @param eventData The payload sent from JS, typed via {@link EventData}
     */
    void onEvent(EventData eventData);
}
