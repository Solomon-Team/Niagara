package me.ayydxn.niagara.javascript;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A typed wrapper around a JSON payload exchanged between Java and JavaScript.
 * <p>
 * Use {@link #of()} to build outgoing payloads from Java, and receive incoming payloads in {@link EventListener} callbacks.
 *
 * @author Ayydxn
 */
public class EventData
{
    private final JsonObject json;

    private EventData(JsonObject json)
    {
        this.json = json;
    }

    /**
     * Creates an empty {@code EventData} for building outgoing payloads.
     */
    public static EventData of()
    {
        return new EventData(new JsonObject());
    }

    /**
     * Parses an {@code EventData} from a raw JSON string received from JavaScript.
     *
     * @param raw The JSON string to parse
     * @return A new {@code EventData} wrapping the parsed object
     * @throws com.google.gson.JsonSyntaxException if {@code raw} is not valid JSON
     */
    public static EventData fromJson(String raw)
    {
        return new EventData(JsonParser.parseString(raw).getAsJsonObject());
    }

    /**
     * Adds a string property and returns {@code this} for chaining.
     */
    public EventData put(String key, String value)
    {
        this.json.addProperty(key, value);

        return this;
    }

    /**
     * Adds a numeric (double) property and returns {@code this} for chaining.
     */
    public EventData put(String key, double value)
    {
        this.json.addProperty(key, value);

        return this;
    }

    /**
     * Adds a boolean property and returns {@code this} for chaining.
     */
    public EventData put(String key, boolean value)
    {
        this.json.addProperty(key, value);

        return this;
    }

    /**
     * Adds an integer property and returns {@code this} for chaining.
     */
    public EventData put(String key, int value)
    {
        this.json.addProperty(key, value);

        return this;
    }

    /**
     * Returns the string value for {@code key}, or {@code null} if absent.
     */
    public String getString(String key)
    {
        return this.json.has(key) ? this.json.get(key).getAsString() : null;
    }

    /**
     * Returns the string value for {@code key}, or {@code defaultValue} if absent.
     */
    public String getString(String key, String defaultValue)
    {
        return this.json.has(key) ? this.json.get(key).getAsString() : defaultValue;
    }

    /**
     * Returns the int value for {@code key}, or {@code 0} if absent.
     */
    public int getInt(String key)
    {
        return this.json.has(key) ? this.json.get(key).getAsInt() : 0;
    }

    /**
     * Returns the int value for {@code key}, or {@code defaultValue} if absent.
     */
    public int getInt(String key, int defaultValue)
    {
        return this.json.has(key) ? this.json.get(key).getAsInt() : defaultValue;
    }

    /**
     * Returns the float value for {@code key}, or {@code 0f} if absent.
     */
    public float getFloat(String key)
    {
        return this.json.has(key) ? this.json.get(key).getAsFloat() : 0f;
    }

    /**
     * Returns the float value for {@code key}, or {@code defaultValue} if absent.
     */
    public float getFloat(String key, float defaultValue)
    {
        return this.json.has(key) ? this.json.get(key).getAsFloat() : defaultValue;
    }

    /**
     * Returns the boolean value for {@code key}, or {@code false} if absent.
     */
    public boolean getBoolean(String key)
    {
        return this.json.has(key) && this.json.get(key).getAsBoolean();
    }

    /**
     * Returns the boolean value for {@code key}, or {@code defaultValue} if absent.
     */
    public boolean getBoolean(String key, boolean defaultValue)
    {
        return this.json.has(key) ? this.json.get(key).getAsBoolean() : defaultValue;
    }

    /**
     * Serializes this payload to a JSON string for passing to JavaScript.
     */
    public String toJson()
    {
        return this.json.toString();
    }
}