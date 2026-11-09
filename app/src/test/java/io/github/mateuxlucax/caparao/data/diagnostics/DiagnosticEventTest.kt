package io.github.mateuxlucax.caparao.data.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

/** Events may leave the phone (#333), so a payload can only be a fixed enum or a number, never free text. */
class DiagnosticEventTest {
    @Test
    fun eventPayloadsCarryOnlyEnumsAndNumbers() {
        val events = DiagnosticEvent::class.java.permittedSubclasses.orEmpty()
        assertTrue("the sealed hierarchy has event types", events.isNotEmpty())
        val objectMethods = Any::class.java.declaredMethods.map { it.name }.toSet()

        val payloadTypes = events.flatMap { event ->
            val constructorTypes = event.declaredConstructors.flatMap { it.parameterTypes.toList() }
            val propertyTypes = event.declaredMethods
                .filter { method ->
                    method.parameterCount == 0 && method.returnType != Void.TYPE && !method.isSynthetic && method.name !in objectMethods
                }
                .map { it.returnType }
            constructorTypes + propertyTypes
        }
        assertTrue("the event types expose payloads", payloadTypes.isNotEmpty())
        for (type in payloadTypes) {
            assertTrue("$type can carry free text", type.isEnum || type.isPrimitive || Number::class.java.isAssignableFrom(type))
        }
    }
}
