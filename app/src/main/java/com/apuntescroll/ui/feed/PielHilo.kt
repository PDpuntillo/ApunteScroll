package com.apuntescroll.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apuntescroll.R
import com.apuntescroll.feed.FiltroFeed
import com.apuntescroll.feed.PostFeed
import com.apuntescroll.feed.TarjetaFeed
import com.apuntescroll.ui.componentes.AvatarMateria
import com.apuntescroll.ui.componentes.margenesDeLista
import kotlin.math.max
import kotlin.math.min

/** Acciones que el feed le pasa a cada publicación. */
class AccionesFeed(
    val alLoSe: (PostFeed) -> Unit,
    val alLoSeTarjeta: (TarjetaFeed) -> Unit,
    val alGuardar: (PostFeed) -> Unit,
    val alVer: (TarjetaFeed) -> Unit,
    /** Abre el hilo completo de la sección de la tarjeta, en esa tarjeta. */
    val alAbrirHilo: (TarjetaFeed) -> Unit,
    val alFiltrar: (FiltroFeed) -> Unit,
    val alVolverAEmpezar: () -> Unit,
)

/**
 * Hilo (estilo X): una tarjeta por publicación, en una lista con separadores. [margenes] no incluye
 * la barra de arriba (la lista empieza debajo de ella).
 */
@Composable
fun FeedHilo(
    estado: EstadoFeed,
    marcas: Map<Long, MarcaTarjeta>,
    margenes: PaddingValues,
    lista: LazyListState,
    acciones: AccionesFeed,
) {
    val visibles by remember(lista) { derivedStateOf { clavesVisibles(lista) } }

    LazyColumn(
        state = lista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = margenesDeLista(margenes),
    ) {
        item(key = "filtros", contentType = "filtros") {
            FilaFiltros(estado.materias, estado.filtro, estado.hayGuardadas, acciones.alFiltrar)
            HorizontalDivider()
        }
        items(estado.posts, key = { it.clave }, contentType = { "hilo" }) { post ->
            val tarjeta = post.primera
            PostHilo(
                post = post,
                marca = marcas[tarjeta.id] ?: MarcaTarjeta(tarjeta.sabida, tarjeta.guardada),
                acciones = acciones,
                conBotonHilo = post.totalSeccion > 1,
            )
            RegistrarVista(clave = post.clave, activo = post.clave in visibles) { acciones.alVer(tarjeta) }
            HorizontalDivider()
        }
        item(key = "fin", contentType = "fin") {
            EstasAlDia(
                vacio = estado.posts.isEmpty(),
                filtro = estado.filtro,
                alVolverAEmpezar = acciones.alVolverAEmpezar,
                alVerTodas = { acciones.alFiltrar(FiltroFeed.Todas) },
            )
        }
    }
}

/**
 * Una tarjeta como publicación del Hilo. También la usa la pantalla del hilo completo. El botón
 * "3/12" (posición en la sección completa) abre el hilo en esta tarjeta.
 */
@Composable
fun PostHilo(post: PostFeed, marca: MarcaTarjeta, acciones: AccionesFeed, conBotonHilo: Boolean) {
    val t = post.primera
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
        AvatarMateria(t.emoji, t.color, tamano = 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            // Materia · documento · página en un renglón. La página no se corta nunca (en el hilo
            // es lo único que cambia de una tarjeta a otra); la materia tiene un tope, y el
            // documento usa lo que queda y se corta con "…" si no entra.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.nombreMateria,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Text(
                    stringResource(R.string.feed_detalle_documento, t.tituloDocumento),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
                )
                Text(
                    stringResource(R.string.feed_detalle_pagina, t.pagina),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (post.conTitulo) {
                Text(
                    t.tituloSeccion ?: stringResource(R.string.seccion_pagina, t.paginaInicioSeccion),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).semantics { heading() },
                )
            }
            Text(
                t.texto,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonLoSe(marca.sabida, alTocar = { acciones.alLoSe(post) })
                BotonGuardar(marca.guardada, alTocar = { acciones.alGuardar(post) })
                Spacer(Modifier.weight(1f))
                if (conBotonHilo) {
                    BotonHilo(t.ordenTarjeta + 1, post.totalSeccion) { acciones.alAbrirHilo(t) }
                }
            }
        }
    }
}

/**
 * Publicaciones que están al menos al 60 % a la vista (o que ocupan el 60 % de la pantalla, si son
 * más altas que ella). Se usa para contar una tarjeta como vista. Cuenta lo que queda entre los
 * márgenes de la lista (lo que está detrás de la barra de navegación no se ve).
 */
fun clavesVisibles(lista: LazyListState): Set<Any> {
    val info = lista.layoutInfo
    val inicio = info.viewportStartOffset + info.beforeContentPadding
    val fin = info.viewportEndOffset - info.afterContentPadding
    val alto = (fin - inicio).coerceAtLeast(1)
    return info.visibleItemsInfo.filter { item ->
        val visible = min(item.offset + item.size, fin) - max(item.offset, inicio)
        item.size > 0 && (visible >= item.size * 0.6f || visible >= alto * 0.6f)
    }.map { it.key }.toSet()
}
