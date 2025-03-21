package io.antivpn.api.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;

/**
 * This class is used to parse json using Google's Gson library.
 */
@UtilityClass
public class GsonParser {

    /**
     * The Gson instance.
     */
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * The pretty Gson instance.
     */
    private final Gson prettyGson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /**
     * Parse a json string to a JsonObject.
     *
     * @param json the json string.
     * @return the JsonObject.
     */
    public JsonObject parse(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    /**
     * Parse a json string to a class.
     *
     * @param json  the json string.
     * @param clazz the class.
     * @param <T>   the class type.
     * @return the class instance.
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    /**
     * Parse a json string to a class.
     *
     * @param json  the json object.
     * @param clazz the class.
     * @param <T>   the class type.
     * @return the class instance.
     */
    public <T> T fromJson(JsonObject json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    /**
     * Parse a json string to a class.
     *
     * @param object the object.
     * @return the json string.
     */
    public String toJson(Object object) {
        return gson.toJson(object);
    }

    /**
     * Parse object to pretty json.
     *
     * @param object the object.
     * @return the json string.
     */
    public String toPrettyJson(Object object) {
        return prettyGson.toJson(object);
    }

}
