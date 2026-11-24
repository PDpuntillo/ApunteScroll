# ApunteScroll: plan del proyecto

App Android que convierte tus PDFs de estudio en un feed para scrollear, con el formato de las
redes sociales. Cuando abres Instagram o X, ApunteScroll se pone encima y te muestra tus
apuntes en vez del feed real. Se publica en **Google Play**.

> Estado: **F0 (base), F1 (PDF → tarjetas) y F2 (feed) implementadas y probadas en la S23**
> con un PDF digital (65 páginas) y un libro escaneado (176 páginas). Lo demás es el plan para
> las fases siguientes.

---

## 1. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Plataforma | App Android nativa en Kotlin + Jetpack Compose, Android 8.0 o más nuevo. Mismo stack que el Bloqueador, actualizado a lo que pide Google Play. |
| "Reemplazar" a IG/X | App propia con feeds al estilo de cada red **más** un servicio de accesibilidad que detecta cuando se abre Instagram o X y la tapa con el feed de apuntes. |
| Contenido | El **texto** de los PDFs, mostrado como texto (no capturas de páginas). Si una página es escaneada, se pasa por **OCR en el teléfono**. |
| Manuscritos | Fuera de alcance por ahora. El OCR apunta a texto impreso. |
| IA | **No** por ahora. Queda como idea futura (sección 12). |
| Datos | Tus apuntes (PDFs, texto y tarjetas) no salen del teléfono. Sin cuentas ni servidor propio. ML Kit (el OCR de Google) sí envía diagnósticos a Google (ver sección 10). |
| Funciones de estudio | Repaso espaciado, preguntas en el feed, materias y filtros, racha y estadísticas. |
| Pase | Para entrar a la red real, estudias 15 tarjetas y ganas 10 minutos (configurable). |
| Tarjetas | ~450 caracteres por defecto (configurable: 300 / 450 / 600). |
| Paquete | `com.apuntescroll` |
| Distribución | **Google Play** con cuenta de desarrollador **personal** (ver sección 10). |

---

## 2. Cómo se usa (flujo)

1. **Importar**: eliges uno o varios PDFs (selector de archivos de Android) y los asignas a una
   materia (nueva o existente).
2. **Procesar**: la app extrae el texto de cada página en segundo plano. Las páginas sin texto
   (escaneadas) pasan por OCR. Se ve el avance ("Leyendo páginas: 12 de 40").
3. **Tarjetas**: el texto se limpia y se corta en *tarjetas* (fragmentos cortos, legibles en el
   celular), agrupadas por sección del apunte.
4. **Scrollear**: el feed muestra las tarjetas con el estilo *Hilo* (tipo X) o *Carrusel*
   (tipo Instagram). "Me gusta" significa **"ya lo sé"**.
5. **Interceptar**: al abrir Instagram o X, aparece el feed de apuntes con el estilo de esa red.
   Para entrar a la red real hay que ganarse un *pase*.

---

## 3. De PDF a tarjetas

### 3.1 Extracción, página por página

| Paso | Cómo |
|---|---|
| Texto digital | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (`PDFTextStripper`, de a una página para no llenar la memoria). Licencia Apache 2.0, Java puro (sin librerías nativas). |
| ¿Es escaneada? | Si la página da menos de ~40 caracteres alfanuméricos, se considera imagen. |
| OCR | Se renderiza la página con `PdfRenderer` de Android (~2000 px de ancho) y se pasa por **ML Kit Text Recognition** (alfabeto latino). Se usa el orden de lectura que da ML Kit (bloques y renglones), que respeta columnas. Se usa la variante que el teléfono descarga por **Google Play services** (APK más chico, y menos riesgo con el requisito de 16 KB de la sección 10). Google Play services baja el modelo al instalar desde Play, o la primera vez que se usa (por ejemplo, después de `adb install`): esa vez necesita internet. El worker reintenta unos 18 minutos sin guardar la página; si el modelo no llegó, el PDF queda en error con "Tocá Reintentar", y Reintentar sigue desde esa página. Solo pasan por OCR las páginas con poco texto que tienen imágenes, y si una página falla se sigue con las demás. |
| Resultado | Texto por página, con su origen (`TEXTO` u `OCR`). |

Corre en WorkManager, en pasadas de hasta 8 minutos (WorkManager corta a los 10), **sin servicio
en primer plano** (cada tipo de servicio en primer plano exige una declaración aparte en Play
Console). Sigue aunque cierres la app. Tiene dos etapas:

1. **Leyendo páginas** (`EXTRAYENDO`): el texto de cada página se guarda apenas sale, así una
   pasada cortada sigue donde quedó. La pantalla de la materia muestra "Leyendo páginas: 12
   de 40" (y la Biblioteca, "Procesando 1 PDF…").
2. **Armando tarjetas** (`ARMANDO`): limpieza y corte de todo el documento junto, porque
   detectar encabezados y pies repetidos necesita ver todas las páginas. Por eso las tarjetas
   aparecen todas juntas al final, no de a una.

Los PDFs se copian a la carpeta privada apenas se eligen (el permiso para leer lo que devuelve
el selector de archivos puede vencer). PdfBox trae BouncyCastle; se excluye `bcpkix`, que
incluye un `X509TrustManager` que acepta cualquier certificado y Google Play rechaza. Solo lo
usan los PDFs cifrados con certificado, que PdfBox rechaza de entrada: quedan en error con un
mensaje claro ("tipo de protección que la app no puede abrir"). Los PDFs con contraseña de
propietario (los que solo bloquean imprimir o copiar) se abren normalmente.

### 3.2 Limpieza (Kotlin puro, con pruebas)

- Normalizar espacios, ligaduras (`ﬁ` → `fi`) y comillas.
- Quitar **encabezados y pies repetidos**: líneas (sin contar números) que aparecen arriba o
  abajo en al menos el 40 % de las páginas. Quitar números de página sueltos.
- Unir palabras cortadas con guion al final de línea (`pala-` + `bra` → `palabra`).
- Unir líneas en **párrafos**: un párrafo termina en línea vacía, en `.` `:` `?` `!` seguido de
  mayúscula o viñeta, o en una línea mucho más corta que el resto.
- Detectar **títulos**: líneas cortas sin punto final que estén numeradas (`1.`, `2.3`), empiecen
  con "Unidad", "Capítulo" o "Tema", o vayan en MAYÚSCULAS.
- Mantener las **listas** (`•`, `-`, `a)`, `1)`) como listas.

### 3.3 Corte en tarjetas

- Una **tarjeta** tiene como mucho ~450 caracteres (unas 3 oraciones, ~20 segundos de lectura).
  El largo se puede cambiar en Ajustes: corta (300), media (450) o larga (600).
- Se corta siempre entre oraciones; solo una oración más larga que el máximo se corta en `;`,
  `,` o entre palabras.
- Los fragmentos de menos de ~80 caracteres se juntan con el vecino.
- Una lista entra entera si cabe; si no, se reparte por ítems.
- Cada tarjeta pertenece a una **sección** (el último título detectado o, si no hay títulos,
  "Página N") y guarda su número de página.
- En cada tarjeta, tocar "p. 12" abre la **página original** renderizada, para tablas, fórmulas
  o gráficos que el texto no captura bien.

### 3.4 Cómo quedó el armado (F1) y sus límites

El código está en `dominio/` (Kotlin puro, ~240 pruebas). Pasó tres rondas de revisión
adversarial con apuntes de texto corrido, libros (también hechos con LaTeX) y diapositivas
exportadas a PDF, y una cuarta con los dos PDFs reales de la prueba en la S23. Además de lo de
3.2 y 3.3, maneja:

- Contadores de página en cualquier parte del encabezado o pie ("Página 5 de 8 · Economía I",
  "Clase 3 | 5 / 20"), numeraciones impresas distintas de las del PDF (también dos en un mismo
  cuadernillo) y encabezados alternados de páginas pares e impares, sin borrar etiquetas reales
  como "Ejercicio 3" o "Unidad 2" ni números que son datos (una respuesta "80", celdas de tablas).
- Notas al pie (con número volado, con `*`, o con número común si el texto las llama): quedan
  después del párrafo, aunque el párrafo siga en la página siguiente.
- Títulos de diapositiva repetidos en varias diapositivas seguidas (se dejan una vez por tanda),
  también con encabezado de plantilla arriba, con viñetas largas o sin viñetas.
- Títulos tipo "Unidad Nº 3", "Unidad Temática 2", "Módulo Introductorio", títulos numerados
  largos pegados al texto, y abreviaturas argentinas (Bs. As., Cód. Civ., Cám. Nac., Expte.).

**Rearmado.** Cada cambio en `dominio/` que cambie las tarjetas sube `ArmadorTarjetas.VERSION`.
Al abrir la app, los PDFs ya listos con una versión anterior se rearman con el texto guardado de
sus páginas (sin volver a leer el PDF ni pasar el OCR) y con el largo con que se armaron. Las
tarjetas cuyo texto no cambió conservan su id y su progreso (lo sé, vistas, guardadas), y las
secciones con el mismo título y página, su id. Mientras tanto Inicio muestra "Actualizando tus
tarjetas…" y no lee el feed, para no mostrar tarjetas que están por reemplazarse. En la S23, el
libro escaneado de 176 páginas se rearma en unos 6 s. Para F4: las tarjetas viejas se borran
antes de insertar las nuevas, así que lo que cuelgue de ellas con borrado en cascada (preguntas)
hay que pasarlo aparte.

Limitaciones conocidas:

- Los títulos en MAYÚSCULAS pasan a minúscula inicial y pierden mayúsculas de nombres propios
  ("Facultad de ciencias económicas").
- Un contador en medio de una línea sin palabra de página ni total ("Economía I 5 FCE") no se
  reconoce como contador.
- Un encabezado de unidad que empieza con la etiqueta ("Unidad 3 – Elasticidad") no se quita si
  cada unidad dura menos de 3 páginas.
- Encabezados de capítulo sin número de página: solo se detectan en 5 o más páginas alternadas.
- Notas con número común que el texto no llama, o bloques de notas de número común de más de 6
  renglones, pueden quedar mezclados con el texto.
- Diapositivas muy cargadas (15 renglones o 700 caracteres) se tratan como páginas de texto.
- Fuera de alcance: columnas múltiples, estructura de tablas, fórmulas y apuntes manuscritos.

---

## 4. Estilos de feed ("pieles")

Los mismos datos se muestran de dos formas, **con identidad propia**: nombres, íconos (Material)
y colores de ApunteScroll, sin logos, colores ni nombres de Instagram o X (ver "Marcas" en la
sección 10). La pantalla que aparece al interceptar muestra siempre el nombre y el logo de
ApunteScroll, para que nadie la confunda con la red real.

### Hilo (estilo X)

- Cada publicación es **una tarjeta**. Avatar = color e iniciales o emoji de la materia;
  nombre = materia; `@documento · p. 12`. Donde el feed entra en una sección, la publicación
  lleva el título de la sección.
- Si la sección tiene varias tarjetas, la publicación muestra un botón `3/12` (posición en la
  sección completa); al tocarlo se abre el hilo completo de la sección en esa tarjeta.
- Acciones: ♥ lo sé · 🔖 guardar (F2); 💬 crear pregunta y 🔁 repasar pronto llegan en F4.
- Al interceptar **X**, se abre este estilo.

### Carrusel (estilo Instagram)

- Cada publicación es **una sección**: hasta 10 tarjetas que se deslizan de costado, con puntitos
  abajo, sobre el color de la materia (4:5, sin pasar del 80 % del alto de la pantalla). La
  primera diapositiva de una sección lleva el título arriba del texto. El texto se achica para
  entrar; si ni así entra, se corta y aparece "Seguir leyendo", que abre el hilo en esa tarjeta.
- Arriba, una fila de **historias = materias** (más "Todas" y "Guardadas"). Tocar una filtra el
  feed. Un anillo de color indica que tiene tarjetas nuevas (desde F4, también repasos
  pendientes).
- Acciones: doble toque = "lo sé" en la diapositiva visible (con TalkBack, es una acción de la
  diapositiva); ♥ = toda la publicación; 🔖 guardar. 💬 crear pregunta (F4) y ↗ compartir el
  texto, más adelante.
- Al interceptar **Instagram**, se abre este estilo.

### Vertical (estilo TikTok), más adelante

Una tarjeta por pantalla completa, letra grande, se pasa deslizando hacia arriba.

---

## 5. Repaso espaciado y armado del feed

### Estados de una tarjeta (Leitner simplificado)

| Evento | Efecto |
|---|---|
| Se ve en el feed (≥ 1,5 s a la vista) | `NUEVA` → `VISTA` (caja 0). Vuelve a salir en 1 día. |
| ♥ "Lo sé" | Sube una caja. Próximo repaso en 1, 3, 7, 16 y 35 días (cajas 1 a 5). |
| Pasa de largo una tarjeta vencida | Sin castigo: se repite al día siguiente, en la misma caja. |
| 🔁 "Repasar pronto" | Vuelve a la caja 0 y reaparece unas 10 publicaciones más abajo. |
| Caja 5 | Cuenta como **dominada** en las estadísticas. |

### Armado del feed (de a 20 publicaciones)

1. Se toman las tarjetas de las materias activas (todas o la del filtro / modo parcial).
2. Se mezclan **nuevas** (en orden del documento, para que se lean de corrido) y **repasos
   vencidos**: unas 6 nuevas por cada 4 repasos, según lo que haya.
3. Cada ~5 publicaciones, una **pregunta** pendiente, si hay.
4. Si no queda nada: publicación "Estás al día ✓" y la opción de seguir con repaso libre
   (tarjetas al azar).

### Cómo funciona en F2 (hasta que llegue F4)

- El feed es una **foto**: se arma al abrir, al cambiar de filtro o de piel y al actualizar
  (deslizar hacia abajo o "Empezar de nuevo"), y no se reacomoda mientras se scrollea. "Lo sé" y
  "guardar" se ven al instante y se guardan en la base.
- Orden: primero las **nuevas**, en orden de lectura (documento en el orden en que se importó,
  sección, tarjeta), alternando materias de a una sección; después las **vistas que no se
  saben**, la vista hace más tiempo primero. Las sabidas no salen, salvo en Guardadas.
- Una tarjeta cuenta como **vista** cuando está al menos al 60 % en pantalla durante 1,5 s, con
  la app en primer plano; se cuenta una vez por sesión (también si se vio en el hilo).
- "Lo sé" la pone en la caja 1 (próximo repaso en un día); deshacerlo la vuelve a la caja 0.
- Si se terminan de procesar PDFs con el feed abierto aparece "Hay tarjetas nuevas"; si se borra
  un PDF, el feed se recarga solo. Cada vez que se lee la base, la lista vuelve arriba (las
  vistas pasaron al final, así que arriba está la próxima sin ver).
- Lo que se marca en el hilo se ve en el feed. Al cambiar de pestaña, un hilo abierto en Inicio
  se cierra: al volver se ve el feed donde estaba.

---

## 6. Preguntas en el feed (sin IA)

- **Manuales**: 💬 en una tarjeta → escribes la pregunta y, si quieres, la respuesta (si no, la
  respuesta es la tarjeta).
- **Automáticas** (se pueden desactivar o borrar de a una), a partir de patrones del texto:
  - *Definición*: "X es / son …", "se define / denomina / llama X a …", "X: …" al comienzo de
    una línea → "¿Qué es X?".
  - *Completar*: la misma definición con el término tapado → "_____ es la rama de …".
- En el feed: tarjeta ❓ con la respuesta oculta → botones **La sabía** / **No la sabía**, con
  el mismo repaso espaciado que las tarjetas.

---

## 7. Interceptar Instagram y X

| Pieza | Detalle |
|---|---|
| Servicio | `AccessibilityService` que escucha solo `typeWindowStateChanged` y lee **solo el nombre del paquete** en primer plano. No lee el contenido de las pantallas de otras apps (`canRetrieveWindowContent="false"`). |
| Apps | `com.instagram.android` → Carrusel; `com.twitter.android` (X) → Hilo. Se declaran en `<queries>` (no se usa `QUERY_ALL_PACKAGES`). La lista se puede ampliar (Instagram Lite, Threads, TikTok). |
| Al detectarla | Abre `FeedInterceptadoActivity` directamente encima de la app (sin pulsar inicio antes, por lo aprendido en el Bloqueador), en su propia tarea, sin aparecer en Recientes, y con un freno contra ráfagas de eventos. |
| Al salir | Atrás o cerrar → va al inicio del teléfono, no de vuelta a la red. |
| Detector intercambiable | La detección va detrás de una interfaz (`DetectorDeApps`). Si Google Play rechazara la accesibilidad, el plan B es `UsageStatsManager` + permiso de superposición (ver sección 10.3). |

### Aviso y consentimiento (lo exige Google Play)

Antes de mandar al usuario a Ajustes de accesibilidad, una **pantalla propia y separada** de
cualquier otro aviso explica:

- qué lee el servicio: **solo qué app está abierta**, nada de lo que se ve en ella;
- para qué: mostrar tus apuntes cuando abres Instagram o X;
- que esa información **no sale del teléfono** ni se guarda;

y pide aceptar con un botón explícito ("Aceptar y activar") o rechazar ("Ahora no"). Si
rechazas, la app sigue funcionando (el feed dentro de la app) y el aviso se puede volver a
abrir desde Ajustes y desde la pantalla de inicio.

### Modos (en Ajustes, por app)

1. **Pase por tarjetas** (por defecto): para entrar a la red real, estudias N tarjetas (15) y
   ganas M minutos (10). Cuando vence el pase, la próxima vez que la red cambie de pantalla
   vuelve el feed de apuntes.
2. **Reemplazo total**: nunca deja pasar a esa red.
3. **Solo recordatorio**: muestra el feed primero, con un botón "Ir igual" que se activa a los
   5 segundos.

Lo que la app **no** hace, porque Google Play lo prohíbe con la accesibilidad: cambiar ajustes,
impedir que se desactive el servicio o se desinstale la app (nada de "modo estricto" como el del
Bloqueador), ni saltarse controles de seguridad de Android.

### A tener en cuenta

- Si el **Bloqueador** también bloquea Instagram o X, las dos apps se van a pelear por la
  pantalla. Hay que quitar esas apps de las listas del Bloqueador, o integrar las dos más
  adelante.
- En Android 17, si el usuario activa la **Protección avanzada**, Android le quita la
  accesibilidad a las apps que no son herramientas de accesibilidad. La app tiene que darse
  cuenta de que el servicio se apagó y explicarlo.

---

## 8. Materias, racha y estadísticas

- **Materia**: nombre, color, emoji y fecha de parcial opcional (muestra cuenta regresiva).
- **Filtros**: las historias del Carrusel, un selector en el Hilo y un **modo parcial** en
  Ajustes que deja una sola materia fija.
- **Registro diario**: tarjetas vistas, "lo sé", preguntas respondidas, tiempo en el feed y
  veces que se interceptó una red.
- **Racha** 🔥: días seguidos en que cumples la meta (20 tarjetas por defecto).
- **Perfil** (con aspecto de perfil de red social): racha, tarjetas dominadas, progreso (%) por
  materia y un gráfico de los últimos 14 días.

---

## 9. Arquitectura técnica

### Stack

| Pieza | Versión / elección |
|---|---|
| compileSdk / targetSdk / minSdk | **37.1** / **36** / 26. Google Play exige apuntar a la API 36 desde el 31/8/2026; se compila contra 37.1 porque las versiones actuales de Compose, core, lifecycle y navigation lo exigen (no cambia el comportamiento de la app). |
| Gradle / AGP / Kotlin / KSP | 9.9.2 / 9.5.2 (con Kotlin incorporado: no se aplica `org.jetbrains.kotlin.android`) / 2.4.21 / 2.3.14 |
| UI | Jetpack Compose + Material 3, **edge-to-edge** (obligatorio con target 36) y "atrás predictivo". |
| Base de datos | Room + KSP |
| Trabajo en segundo plano | WorkManager (sin servicio en primer plano) |
| Navegación | Navigation Compose 2.10 con rutas con tipos (`@Serializable`). Cada sección de la barra de abajo es un grafo, así guarda su propia pila de pantallas. |
| PDF → texto | PdfBox-Android |
| OCR | ML Kit Text Recognition (vía Google Play services) |
| Ajustes | SharedPreferences (como el Bloqueador) |
| Publicación | Android App Bundle (`.aab`) firmado con clave de subida + Play App Signing |

En esta PC ya están instaladas las plataformas 36 y 37.1 y build-tools 36.1.0. No hay Android
Studio: se compila con el wrapper de Gradle (ver README).

Un solo módulo `app`, con inyección de dependencias manual (un contenedor en `Application`),
igual que el Bloqueador.

### Paquetes

```
com.apuntescroll
├── datos/         Room: entidades, DAOs, base; ajustes
├── pdf/           extracción (PdfBox), OCR (PdfRenderer + ML Kit), worker de procesamiento, rearmado
├── dominio/       Kotlin puro y probado: limpieza y corte en tarjetas (después, repaso y preguntas)
├── feed/          Kotlin puro y probado: orden del feed y publicaciones de Hilo y Carrusel
├── intercepcion/  servicio de accesibilidad, aviso y consentimiento, pantalla interceptada, pases
└── ui/            MainActivity, navegación, biblioteca, feed (Hilo/Carrusel), perfil, ajustes
```

### Modelo de datos (Room)

| Entidad | Campos principales |
|---|---|
| `Materia` | id, nombre, color, emoji, fechaParcial?, creada |
| `Documento` | id, materiaId, título, archivo, páginas, estado (`PENDIENTE`/`EXTRAYENDO`/`ARMANDO`/`LISTO`/`ERROR`), páginasProcesadas, páginasOcr, error, creado, versiónArmado, largoArmado (para rearmar, 3.4) |
| `PaginaTexto` | documentoId + número, texto crudo, origen (`TEXTO`/`OCR`). Permite retomar el procesamiento y rearmar las tarjetas. |
| `Seccion` | id, documentoId, orden, título, páginaInicio |
| `Tarjeta` | id, documentoId, seccionId, orden, texto, página, origen (`TEXTO`/`OCR`), caja, próximoRepaso?, vecesVista, últimaVista?, guardada, editada |
| `Pregunta` | id, tarjetaId, pregunta, respuesta, tipo (`MANUAL`/`DEFINICION`/`COMPLETAR`), caja, próximoRepaso? |
| `RegistroDia` | fecha, tarjetasVistas, loSe, preguntas, segundosEnFeed, intercepciones |

Los PDFs se copian a la carpeta privada de la app, así siguen disponibles aunque se borre el
original.

La base va por la versión 2 (la 1 → 2 agrega `versiónArmado` y `largoArmado`, con migración
automática de Room). Los esquemas quedan en `app/schemas`; antes de publicar se saca el borrado
de la base como red de seguridad y cada cambio lleva su migración.

### Pruebas

- Pruebas locales (JUnit) de `dominio/` y `feed/` (limpieza, corte, orden del feed y
  publicaciones; después repaso y detector de preguntas), con textos de ejemplo de apuntes
  reales, y del traspaso de progreso al rearmar.
- Extracción, OCR y pantallas: pruebas a mano en la S23 con un PDF digital y uno escaneado.

---

## 10. Publicación en Google Play

### 10.1 Requisitos y cómo los cumplimos

| Requisito | Qué implica para ApunteScroll |
|---|---|
| **Cuenta de desarrollador** | **Personal** (ver 10.5). Pago único de US$25 con tarjeta de crédito o débito (no prepaga); si los datos de identidad no son válidos, no se devuelve. Hay que ser mayor de 18. Verificación: DNI (o pasaporte), comprobante de domicilio de los últimos 90 días que coincida exacto con el perfil de pagos, correo y teléfono de contacto y correo de desarrollador. |
| **Verificación de dispositivo** (cuentas personales) | Probar que tienes un Android físico, sin root, con Android 10 o más, desde la app Play Console. La S23 sirve. |
| **Estado de comerciante** ("trader", desde nov. 2026) | Toda cuenta nueva declara al crearse si es comerciante según las leyes de la UE y el Reino Unido. Una app gratis, sin anuncios ni compras, y hecha como estudiante es en principio **no comerciante**. Si te declaras comerciante, tu dirección y tu teléfono pasan a ser públicos. |
| **Cuenta inactiva** | Una cuenta con más de un año que nunca envió una app a revisión se cierra, sin devolver los US$25. Conviene crearla cuando falte poco para F3, no mucho antes. |
| **Prueba cerrada** (cuentas personales creadas después del 13/11/2023) | Antes de poder publicar en producción: una prueba cerrada con **al menos 12 testers** inscritos **14 días seguidos** al momento de pedir el acceso. Un tester que sale antes de los 14 días no cuenta, y si vuelve a entrar, empieza de cero. La prueba interna **no** cuenta. Después se pide el acceso a producción con un formulario (cómo fue reclutar, si usaron todas las funciones, qué opinaron, qué cambiaste); la revisión suele tardar hasta 7 días y puede pedir más prueba si hubo "poco uso". Ver 10.6. |
| **API objetivo 36** (desde el 31/8/2026) | `targetSdk = 36`, con sus cambios: edge-to-edge obligatorio y "atrás predictivo". |
| **Formato** | Android App Bundle (`.aab`), firmado con una clave de subida que se guarda **fuera del repo**; Google firma la versión final (Play App Signing). |
| **Páginas de memoria de 16 KB** | Las apps con target 35 o más tienen que soportarlas en dispositivos de 64 bits. PdfBox es Java puro; para ML Kit se usa la variante de Play services. Se comprueba en el *App Bundle Explorer* de Play Console ("Supports 16 KB"). |
| **Accesibilidad** (no es una herramienta de accesibilidad) | No se puede marcar `isAccessibilityTool`. Hace falta: (1) **declaración en Play Console** con el motivo ("funcionalidad de la app"), si se recopilan datos con la API y un **video**; (2) mencionar el uso de accesibilidad en la **ficha** de Play; (3) el **aviso y consentimiento** dentro de la app (sección 7); (4) explicar por qué no alcanza una API más acotada (ver 10.3). Si cambia el uso, se vuelve a declarar. |
| **Política de privacidad** | URL pública. Se publica desde este repo con GitHub Pages (`docs/privacidad.md`). |
| **Seguridad de los datos** | Formulario en Play Console. Los apuntes no salen del teléfono, pero **ML Kit (variante de Play services) envía a Google** información del dispositivo y de la app, métricas de rendimiento y códigos de error, con identificadores del dispositivo y de la instalación, para diagnóstico y analítica ([divulgación de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure)). Respuestas: recopila datos **sí** (por el SDK); tipos "ID del dispositivo u otros" y "Información y rendimiento de la app > Diagnóstico"; se comparten **no**; finalidad analítica; cifrados en tránsito; no opcionales. Revisar la página de ML Kit al completar el formulario, porque cambia con cada versión. La política de privacidad tiene que decir lo mismo. |
| **Contenido de la app** | Cuestionario de clasificación por edad, público objetivo **mayores de 18** (universitarios; evita la política de Familias), sin anuncios. |
| **Marcas y suplantación** | Nombre e ícono propios. Sin logos, colores ni capturas de Instagram o X. En la ficha, las redes se nombran solo de forma descriptiva ("cuando abres tus redes sociales…"), nunca como si fuera una app oficial. |
| **Ficha de la tienda** | Ícono de 512×512, gráfico destacado de 1024×500, al menos 2 capturas de teléfono, descripción corta (80 caracteres) y completa (4000). |

Aparte: la **verificación de desarrolladores de Android** (empieza el 30/9/2026 en cuatro países
y llega a todo el mundo en 2027) afecta a las apps que se instalan por APK, como el Bloqueador.
Para ApunteScroll alcanza con la verificación de la cuenta de Play Console.

### 10.2 Video para la declaración de accesibilidad

Grabación de pantalla en la S23 (subida a YouTube como "no listado") que muestre, en orden:

1. Abrir la app.
2. Llegar a la pantalla de aviso y consentimiento, con todo el texto visible.
3. Aceptar y activar el servicio en Ajustes de accesibilidad.
4. Rechazar, y volver a abrir el aviso.
5. La función principal: abrir Instagram y que aparezca el feed de apuntes (con subtítulos que
   lo expliquen).

### 10.3 Por qué accesibilidad, y plan B

La política pide usar APIs más acotadas cuando alcancen. La alternativa sería consultar
`UsageStatsManager` cada segundo para saber qué app está en primer plano. Eso necesita un
servicio corriendo todo el tiempo (con su propia declaración), gasta batería, reacciona con
retraso y, además, para abrir la pantalla encima hace falta el permiso de superposición. La
accesibilidad avisa al instante y sin servicio extra. Ese es el argumento para la declaración.

Si Google rechaza la declaración, se cambia la implementación de `DetectorDeApps` por la de
`UsageStatsManager` + superposición, sin tocar el resto de la app.

### 10.4 Calendario de publicación

1. Al terminar **F3** (MVP): crear la cuenta (si no existe), completar la configuración de la
   app (ficha, política de privacidad, clasificación, seguridad de los datos, declaración de
   accesibilidad y video). Google exige completar la configuración antes de abrir la prueba
   cerrada.
2. Prueba **interna** solo con tu propia cuenta, para revisar que todo funcione. Los testers
   **no** entran acá: quien se inscribe en una prueba interna deja de poder entrar a la cerrada.
3. **Prueba cerrada** con 15–20 testers durante al menos 14 días. Mientras tanto se desarrollan
   F4 y F5, y se suben actualizaciones a la misma prueba.
4. Solicitud de acceso a **producción**, con lo aprendido en la prueba.

Las versiones de prueba cerrada también pasan por revisión, así que lo más probable es que la
declaración de accesibilidad se revise ahí. Hay que tenerla lista antes del paso 3.

### 10.5 Cuenta personal u organización

| | Personal | Organización |
|---|---|---|
| Para quién (según Google) | Uso personal: estudiantes, aficionados, desarrolladores amateurs. | Empresas u organizaciones con actividad comercial, profesional o de gobierno. |
| Obligatoria para | — | Apps financieras, de salud, que usan `VpnService` o de gobierno. ApunteScroll no entra en ninguna. |
| Costo y funciones | US$25 una vez. Mismas funciones; puede cobrar o vender. | Igual. |
| Requisitos extra | DNI + comprobante de domicilio; verificación de dispositivo. | **Número D-U-N-S** (gratis, hasta ~30 días; en Argentina lo gestiona CIAL D&B y no está claro si se lo dan a un monotributista), documento de la organización (Constancia de inscripción de CUIT, certificado de vigencia o extracto del registro), **sitio web verificado** y teléfono verificado. |
| Prueba de 12 testers × 14 días | **Sí.** | No (Google la limita a cuentas personales). |
| Qué se ve en Google Play | Nombre legal, país y correo de desarrollador. La dirección completa solo si cobras en Play o te declaras comerciante. | Nombre legal, D-U-N-S, dirección legal, correo y **teléfono** de desarrollador. |
| Cambiar después | Se puede pasar a organización (cuenta verificada, web verificada y D-U-N-S). | No se puede volver a personal. |

**Decisión: cuenta personal.** Eres justo el caso para el que Google la pensó, no necesitas
empresa ni D-U-N-S, y muestra menos datos tuyos. Si en el futuro ApunteScroll se convierte en
un emprendimiento, la cuenta se puede pasar a organización. El costo es la prueba con 12
testers (10.6).

### 10.6 Cómo conseguir los testers

Lo que dice Google: los testers necesitan una cuenta de Google (la misma que usan en Play Store),
se agregan por lista de correos o por un Google Group (`@googlegroups.com`) y cada uno se
inscribe con un enlace. La app tiene que estar disponible en su país (Argentina). Google
recomienda reclutar entre conocidos (nombra a los compañeros de estudio), pedir que usen
muchas funciones, dar un canal de opiniones y avisarles que tienen que quedarse 14 días.

Plan:

1. **Reclutar 15–20 personas**, no 12, para tener margen: compañeros de cursada, grupos de
   WhatsApp de las materias, el centro de estudiantes, amigos y familia con Android. La app les
   sirve de verdad para estudiar, así que el uso va a ser real (es lo que Google mira).
2. Juntar sus correos de Google mientras se desarrolla F1–F3 (hay tiempo).
3. Crear un **Google Group** (`apuntescroll-testers@googlegroups.com`) y un **grupo de
   WhatsApp** para avisos y opiniones. En la prueba cerrada se configura un formulario de
   opiniones (Google Forms) que aparece en la página de inscripción.
4. Instrucciones para cada tester: inscribirse con la misma cuenta de Play Store, instalar,
   **no salir de la prueba**, subir al menos un apunte de una materia y usarla varias veces por
   semana.
5. Revisar cada tanto que el número de inscritos no baje de 12.

No conviene usar los grupos de "intercambio de testers" ni pagar servicios de testers: Google no
tiene una postura publicada, pero castiga la actividad falsa, y una cuenta cerrada no se
recupera.

---

## 11. Fases

Cada fase termina con una versión que se puede instalar y probar en la S23.

| Fase | Qué incluye | Lista cuando… |
|---|---|---|
| **F0. Base** ✅ | Proyecto Android con target 36, edge-to-edge, tema, barra de navegación con secciones vacías, Ajustes con el largo de tarjeta, README con cómo compilar. | La versión debug abre en la S23 ✓. |
| **F1. PDF → tarjetas** ✅ | Materias, importar PDFs, worker con texto + OCR, limpieza y corte, biblioteca con progreso, lista de tarjetas por documento, ver la página original. | Un PDF digital y uno escaneado tuyos quedan en tarjetas legibles ✓ (65 páginas → 423 tarjetas; libro escaneado de 176 páginas → 3102 tarjetas, OCR en unos 2 min). |
| **F2. Feed** ✅ | Pieles Hilo y Carrusel, "lo sé", guardar, marcar vistas, historias/filtro por materia, hilo completo de una sección, rearmado cuando cambia el armado. | Scrolleas tus apuntes en los dos estilos ✓. |
| **F3. Intercepción** | Servicio de accesibilidad, aviso y consentimiento, pantalla interceptada, modos y pases. | Abrir Instagram muestra el Carrusel y abrir X muestra el Hilo. |
| **P1. Publicación (prueba)** | Clave de subida, activar R8 en la versión release (y probarla con un PDF digital y uno escaneado), pantalla de licencias de código abierto, política de privacidad, ficha, formularios de Play Console, declaración y video, prueba cerrada. | La prueba cerrada está abierta con 12+ testers. |
| **F4. Repaso y preguntas** | Leitner, mezcla del feed, preguntas manuales y automáticas, "Estás al día". | Las tarjetas que marcas vuelven en las fechas correctas. |
| **F5. Racha y estadísticas** | Registro diario, metas, perfil, gráfico. | La racha y el progreso por materia se ven bien después de unos días de uso. |
| **F6. Pulido** | Editar tarjetas (corregir OCR), copia de seguridad (exportar e importar un .zip), modo oscuro, ícono final. | — |
| **P2. Producción** | Solicitud de acceso a producción y lanzamiento. | ApunteScroll está en Google Play. |

**MVP = F0 a F3**: ya reemplaza a Instagram y X con tus apuntes. F4 y F5 se hacen mientras
corre la prueba cerrada.

### Qué tienes que hacer tú (en paralelo)

- Ir juntando **15–20 testers** con Android (sección 10.6).
- Hacia el final de F3: crear la **cuenta personal** de Google Play (US$25), verificar identidad
  y dispositivo, y declararte no comerciante.

---

## 12. Ideas para después (fuera de alcance ahora)

- Piel **Vertical** (TikTok) e interceptar TikTok.
- **IA opcional** con la API de Claude y la API key de cada usuario: resúmenes, hilos y
  preguntas generados. Costo estimado por cada 100 páginas: ~US$0,08 con Haiku 5.5,
  ~US$1,60 con Sonnet 5.5 y ~US$3,20 con Opus 5.5 (la mitad con Batch).
- Soporte para **apuntes manuscritos**.
- **Nube y compartir** apuntes con compañeros (cuentas + backend). Implicaría la política de
  contenido generado por usuarios de Google Play.
- Integración con el **Bloqueador**.
- Versión para PC (extensión de navegador que reemplaza el feed de instagram.com y x.com).

---

## 13. Riesgos y limitaciones conocidas

| Riesgo | Mitigación |
|---|---|
| Google Play **rechaza la declaración de accesibilidad**. | Aviso y video bien hechos, alcance mínimo (solo el paquete); plan B con `UsageStatsManager` (10.3). |
| La prueba cerrada no llega a 12 testers o Google la considera con **poco uso**. | Reclutar de más (15–20) y pedirles que usen la app de verdad durante los 14 días. |
| PDFs con columnas, tablas o fórmulas: el texto sale desordenado o con símbolos sueltos. | Botón para ver la página original; editar tarjetas (F6). |
| OCR lento en documentos largos (~0,5–1,5 s por página). | Worker en segundo plano por tandas, con el avance a la vista ("Leyendo páginas: 12 de 40"). |
| Instagram o X cambian de paquete o de comportamiento. | Lista de paquetes configurable. |
| Android sigue restringiendo la accesibilidad (por ejemplo, la Protección avanzada de Android 17). | Detectar el servicio apagado y explicarlo; el feed dentro de la app sigue funcionando. |

---

## 14. Pendientes

- Probar el feed con letra grande y con TalkBack ("Seguir leyendo", acciones de las
  diapositivas y de las historias).
- Rearmar las tarjetas al cambiar el largo en Ajustes (hoy vale solo para los PDFs nuevos); el
  progreso de las tarjetas se perdería casi todo, así que pediría confirmación.
- Reclutar testers (10.6).

---

## Fuentes (Google Play, consultadas el 23/11/2026)

- [Uso de la API AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Permisos y APIs que acceden a información sensible](https://support.google.com/googleplay/android-developer/answer/16909972)
- [Requisitos de prueba para cuentas personales nuevas](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Elegir el tipo de cuenta de desarrollador](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Información requerida por tipo de cuenta](https://support.google.com/googleplay/android-developer/answer/13628312)
- [Verificación de identidad y documentos (Argentina)](https://support.google.com/googleplay/android-developer/answer/15633622?co=GENIE.CountryCode%3DAR)
- [Verificación de dispositivo](https://support.google.com/googleplay/android-developer/answer/14316361)
- [Cambiar de cuenta personal a organización](https://support.google.com/googleplay/android-developer/answer/16260648)
- [Estado de comerciante (trader)](https://support.google.com/googleplay/android-developer/answer/17594752)
- [Cuentas inactivas](https://support.google.com/googleplay/android-developer/answer/11605267)
- [Configurar pruebas abiertas, cerradas e internas](https://support.google.com/googleplay/android-developer/answer/9845334)
- [Requisitos de nivel de API objetivo](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Compatibilidad con páginas de 16 KB](https://developer.android.com/guide/practices/page-sizes)
- [Restricciones para abrir actividades desde segundo plano](https://developer.android.com/guide/components/activities/background-starts)
- [Verificación de desarrolladores de Android](https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console)
