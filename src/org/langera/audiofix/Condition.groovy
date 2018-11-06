package org.langera.audiofix

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class Condition {

    enum Type {
        MUST('+') {
            @Override
            boolean meetsCondition(final List<String> tokens, final String condition) {
                return tokens.any { key -> key.contains(condition) }
            }
        },
        MUST_NOT('-') {
            @Override
            boolean meetsCondition(final List<String> tokens, final String condition) {
                return tokens.every { key -> !key.contains(condition) }
            }
        };

        String sign

        Type(sign) {
            this.sign = sign
        }

        abstract boolean meetsCondition(List<String> tokens, String condition)
    }

    Type type
    String condition

    Condition(String text) {
        this(Type.values().find { it.sign == text.substring(0, 1) }, text.substring(1))
    }

    Condition(final Type type, final String condition) {
        this.type = type
        this.condition = condition
    }

    boolean meetsCondition(List<String> tokens, boolean explain) {
        boolean result = type.meetsCondition(tokens, condition)
        if (explain) {
            println "condition $type: $condition for tokens $tokens = $result"
        }
        return result
    }

    @Override
    String toString() {
        return type.sign + condition
    }
}
