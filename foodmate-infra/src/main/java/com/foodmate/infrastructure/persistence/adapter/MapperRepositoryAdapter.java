package com.foodmate.infrastructure.persistence.adapter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.FactoryBean;

/** Exposes a MyBatis mapper through an application-owned repository contract. */
public abstract class MapperRepositoryAdapter<T> implements FactoryBean<T> {
    private final Object mapper;
    private final Class<T> repositoryType;
    private volatile T proxy;

    protected MapperRepositoryAdapter(Object mapper, Class<T> repositoryType) {
        this.mapper = mapper;
        this.repositoryType = repositoryType;
    }

    @Override
    public T getObject() {
        T current = proxy;
        if (current != null) return current;
        synchronized (this) {
            if (proxy == null) {
                proxy =
                        repositoryType.cast(
                                Proxy.newProxyInstance(
                                        repositoryType.getClassLoader(),
                                        new Class<?>[] {repositoryType},
                                        this::invoke));
            }
            return proxy;
        }
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryType;
    }

    private Object invoke(Object ignored, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> repositoryType.getSimpleName() + "Adapter";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> ignored == args[0];
                default -> null;
            };
        }
        Method mapperMethod =
                findMethod(mapper.getClass(), method.getName(), method.getParameterTypes());
        if (mapperMethod == null) {
            throw new NoSuchMethodException(method.toGenericString());
        }
        try {
            return mapperMethod.invoke(mapper, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            for (Class<?> contract : type.getInterfaces()) {
                Method method = findMethod(contract, name, parameterTypes);
                if (method != null) return method;
            }
            Class<?> parent = type.getSuperclass();
            return parent == null ? null : findMethod(parent, name, parameterTypes);
        }
    }
}
