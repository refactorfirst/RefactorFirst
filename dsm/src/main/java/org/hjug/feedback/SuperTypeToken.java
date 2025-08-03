package org.hjug.feedback;

import java.lang.reflect.*;

public abstract class SuperTypeToken<T> {
    private final Type type;

    protected SuperTypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        } else {
            throw new RuntimeException("Missing type parameter.");
        }
    }

    public Type getType() {
        return type;
    }

    public Class<?> getClassFromType() {
        return getClassFromType(type);
    }

    // ((ParameterizedType) type).getActualTypeArguments()[0] - returns String in List<String>
    static Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            return java.lang.reflect.Array.newInstance(getClassFromType(componentType), 0)
                    .getClass();
        } else if (type instanceof TypeVariable<?>) {
            // Type variables don't have a direct class representation
            return Object.class; // Fallback
        } else if (type instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) type).getUpperBounds();
            return getClassFromType(upperBounds[0]); // Use the first upper bound
        }
        throw new IllegalArgumentException("Unsupported Type: " + type);
    }
}
