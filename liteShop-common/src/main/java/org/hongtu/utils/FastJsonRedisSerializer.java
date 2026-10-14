package org.hongtu.utils;

import com.alibaba.fastjson.JSON;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;

/**
 * Redis使用FastJson序列化
 *
 * @author sg
 */
public class FastJsonRedisSerializer<T> implements RedisSerializer<T>
{

    private final Class<T> clazz;

    public FastJsonRedisSerializer(Class<T> clazz)
    {
        super();
        this.clazz = clazz;
    }

    @Override
    public byte[] serialize(T t) throws SerializationException
    {
        if (t == null)
        {
            return new byte[0];
        }
        // 不写入可控类型信息，避免反序列化时触发危险的 AutoType 路径。
        return JSON.toJSONString(t).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException
    {
        if (bytes == null || bytes.length <= 0)
        {
            return null;
        }
        String str = new String(bytes, StandardCharsets.UTF_8);

        return JSON.parseObject(str, clazz);
    }
}
