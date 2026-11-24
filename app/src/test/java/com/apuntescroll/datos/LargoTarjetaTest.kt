package com.apuntescroll.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class LargoTarjetaTest {

    @Test
    fun `el largo por defecto es 450 caracteres`() {
        assertEquals(450, LargoTarjeta.PREDETERMINADO.maxCaracteres)
    }

    @Test
    fun `lee el nombre guardado`() {
        assertEquals(LargoTarjeta.CORTA, LargoTarjeta.desdeNombre("CORTA"))
        assertEquals(LargoTarjeta.LARGA, LargoTarjeta.desdeNombre("LARGA"))
    }

    @Test
    fun `sin valor o con un valor desconocido usa el predeterminado`() {
        assertEquals(LargoTarjeta.MEDIA, LargoTarjeta.desdeNombre(null))
        assertEquals(LargoTarjeta.MEDIA, LargoTarjeta.desdeNombre("GIGANTE"))
    }
}
