package org.hjug.feedback;

import java.lang.reflect.*;

public abstract class SuperTypeToken<T> {
    private final Type type;

    protected SuperTypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType parameterizedType) {
            this.type = parameterizedType.getActualTypeArguments()[0];
        } else {
            throw new RuntimeException("Missing type parameter.");
        }
    }

    public Type getType() {
        return type;
    }

    public Class<T> getClassFromTypeToken() {
        return (Class<T>) getClassFromTypeToken(type);
    }

    // ((ParameterizedType) type).getActualTypeArguments()[0] - returns String in List<String>
    static Class<?> getClassFromTypeToken(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        } else if (type instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getRawType();
        } else if (type instanceof GenericArrayType arrayType) {
            Type componentType = arrayType.getGenericComponentType();
            return java.lang.reflect.Array.newInstance(getClassFromTypeToken(componentType), 0)
                    .getClass();
        } else if (type instanceof TypeVariable<?>) {
            // Type variables don't have a direct class representation
            return Object.class; // Fallback
        } else if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            return getClassFromTypeToken(upperBounds[0]); // Use the first upper bound
        }
        throw new IllegalArgumentException("Unsupported Type: " + type);
    }
}
