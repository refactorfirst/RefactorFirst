package org.hjug.feedback;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuperTypeTokenTest {

    SuperTypeToken<DefaultWeightedEdge> token;

    @BeforeEach
    void setUp() {
        token = new SuperTypeToken<>() {};
    }

    @Test
    void getType() {
        assertEquals(
                "class org.jgrapht.graph.DefaultWeightedEdge", token.getType().toString());
    }

    @Test
    void getGenericType() {
        SuperTypeToken<List<String>> genericToken = new SuperTypeToken<>() {};
        assertEquals("java.util.List<java.lang.String>", genericToken.getType().toString());
        assertEquals(List.class, genericToken.getClassFromTypeToken());
    }

    @Test
    void getClassFromType() {
        assertEquals(DefaultWeightedEdge.class, token.getClassFromTypeToken());
    }

    @Test
    void typeWithGenericParameter() {
        assertEquals(DefaultWeightedEdge.class, new GenericTestClass<>(token).getTypeTokenClass());
    }
}

class GenericTestClass<T> {
    SuperTypeToken<T> typeToken;

    public GenericTestClass(SuperTypeToken<T> token) {
        this.typeToken = token;
    }

    public Class<T> getTypeTokenClass() {
        return typeToken.getClassFromTypeToken();
    }
}
