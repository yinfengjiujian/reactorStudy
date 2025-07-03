package com.duanml.reactorservice.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * <p>Title: com.duanml.reactorstudy.utils</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/3 19:47
 * Description: No Description
 */
@Component
public class JacksonUtil {

    private static ObjectMapper OBJECT_MAPPER;

    // 通过构造器注入 JacksonConfig 中注册的 ObjectMapper
    public JacksonUtil(ObjectMapper injectedMapper) {
        JacksonUtil.OBJECT_MAPPER = injectedMapper;
    }

    // 对象转 JSON 字符串
    public static String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化对象失败：" + obj, e);
        }
    }

    // JSON 字符串转对象
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException("反序列化失败，目标类型：" + clazz.getName(), e);
        }
    }

    /**
     * JSON字符串转对象，支持复杂泛型
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || typeRef == null) return null;
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    // JSON 转 List<T>
    public static <T> List<T> fromJsonToList(String json, Class<T> elementClass) {
        if (json == null || json.isEmpty()) return null;
        try {
            CollectionType listType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass);
            return OBJECT_MAPPER.readValue(json, listType);
        } catch (IOException e) {
            throw new RuntimeException("反序列化 List 失败，元素类型：" + elementClass.getName(), e);
        }
    }

    // JSON 转 Map<K, V>
    public static <K, V> Map<K, V> fromJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null || json.isEmpty()) return null;
        try {
            MapType mapType = OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
            return OBJECT_MAPPER.readValue(json, mapType);
        } catch (IOException e) {
            throw new RuntimeException("反序列化 Map 失败，类型：" + keyClass.getName() + "->" + valueClass.getName(), e);
        }
    }

}
