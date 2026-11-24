# ApunteScroll: plan del proyecto

App Android que convierte tus PDFs de estudio en un feed para scrollear, con el formato de las
redes sociales. Cuando abres Instagram o X, ApunteScroll se pone encima y te muestra tus
apuntes en vez del feed real. Se publica en **Google Play**.

> Estado: **F0 (base) hecha**: compila y pasa pruebas y lint; falta probarla en la S23. Lo
> demás es el plan para las fases siguientes.

---

## 1. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Plataforma | App Android nativa en Kotlin + Jetpack Compose, Android 8.0 o más nuevo. Mismo stack que el Bloqueador, actualizado a lo que pide Google Play. |
| "Reemplazar" a IG/X | App propia con feeds al estilo de cada red **más** un servicio de accesibilidad que detecta cuando se abre Instagram o X y la tapa con el feed de apuntes. |
| Contenido | El **texto** de los PDFs, mostrado como texto (no capturas de páginas). Si una página es escaneada, se pasa por **OCR en el teléfono**. |
| Manuscritos | Fuera de alcance por ahora. El OCR apunta a texto impreso. |
| IA | **No** por ahora. Queda como idea futura (sección 12). |
| Datos | Todo local en el teléfono. Sin cuentas ni servidor. |
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
   (escaneadas) pasan por OCR. Se ve el avance ("Procesando 12/40").
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
| OCR | Se renderiza la página con `PdfRenderer` de Android (~2000 px de ancho) y se pasa por **ML Kit Text Recognition** (alfabeto latino). Los bloques se ordenan de arriba abajo y de izquierda a derecha. Se usa la variante que el teléfono descarga por **Google Play services** (APK más chico, y menos riesgo con el requisito de 16 KB de la sección 10). Necesita internet una sola vez, para bajar el modelo. |
| Resultado | Texto por página, con su origen (`TEXTO` u `OCR`). |

Corre en WorkManager, de a tandas cortas de páginas, **sin servicio en primer plano** (cada tipo
de servicio en primer plano exige una declaración aparte en Play Console). Sigue aunque cierres
la app y las tarjetas aparecen a medida que se procesan.

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

---

## 4. Estilos de feed ("pieles")

Los mismos datos se muestran de dos formas, **con identidad propia**: nombres, íconos (Material)
y colores de ApunteScroll, sin logos, colores ni nombres de Instagram o X (ver "Marcas" en la
sección 10). La pantalla que aparece al interceptar muestra siempre el nombre y el logo de
ApunteScroll, para que nadie la confunda con la red real.

### Hilo (estilo X)

- Cada publicación es **una tarjeta**. Avatar = color e iniciales o emoji de la materia;
  nombre = materia; `@documento · p. 12`.
- Si la sección tiene varias tarjetas, la publicación muestra `1/4`; al tocarla se abre el hilo
  completo de la sección.
- Acciones: 💬 crear pregunta · 🔁 repasar pronto · ♥ lo sé · 🔖 guardar.
- Al interceptar **X**, se abre este estilo.

### Carrusel (estilo Instagram)

- Cada publicación es **una sección**: hasta 10 tarjetas que se deslizan de costado, con puntitos
  abajo. La primera diapositiva es el título de la sección sobre el color de la materia.
- Arriba, una fila de **historias = materias**. Tocar una filtra el feed a esa materia. Un anillo
  de color indica que tiene repasos pendientes.
- Acciones: doble toque = "lo sé" en la diapositiva visible; ♥ = toda la publicación;
  💬 crear pregunta; ↗ compartir el texto; 🔖 guardar.
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
| Navegación | Se decide en F1: Navigation Compose 2.10 o Navigation 3 (1.2, estable). En F0 la barra de abajo cambia de sección sin librería. |
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
├── pdf/           extracción (PdfBox), OCR (PdfRenderer + ML Kit), worker de procesamiento
├── dominio/       Kotlin puro y probado: limpieza, corte en tarjetas, repaso, armado del feed, preguntas
├── intercepcion/  servicio de accesibilidad, aviso y consentimiento, pantalla interceptada, pases
└── ui/            MainActivity, navegación, biblioteca, feed (Hilo/Carrusel), perfil, ajustes
```

### Modelo de datos (Room)

| Entidad | Campos principales |
|---|---|
| `Materia` | id, nombre, color, emoji, fechaParcial?, creada |
| `Documento` | id, materiaId, título, archivo, páginas, estado (`PENDIENTE`/`PROCESANDO`/`LISTO`/`ERROR`), páginasProcesadas, creado |
| `Seccion` | id, documentoId, orden, título, páginaInicio |
| `Tarjeta` | id, documentoId, seccionId, orden, texto, página, origen (`TEXTO`/`OCR`), caja, próximoRepaso?, vecesVista, últimaVista?, guardada, editada |
| `Pregunta` | id, tarjetaId, pregunta, respuesta, tipo (`MANUAL`/`DEFINICION`/`COMPLETAR`), caja, próximoRepaso? |
| `RegistroDia` | fecha, tarjetasVistas, loSe, preguntas, segundosEnFeed, intercepciones |

Los PDFs se copian a la carpeta privada de la app, así siguen disponibles aunque se borre el
original.

### Pruebas

- Pruebas locales (JUnit) de todo `dominio/`: limpieza, corte, repaso, feed y detector de
  preguntas, con textos de ejemplo de apuntes reales.
- Extracción y OCR: pruebas a mano en la S23 con un PDF digital y uno escaneado.

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
| **Seguridad de los datos** | Formulario en Play Console. ApunteScroll no envía datos fuera del teléfono; falta revisar qué declara ML Kit (puede enviar métricas de uso a Google). |
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
| **F0. Base** ✅ | Proyecto Android con target 36, edge-to-edge, tema, barra de navegación con secciones vacías, Ajustes con el largo de tarjeta, README con cómo compilar. | La versión debug abre en la S23 (falta probarlo). |
| **F1. PDF → tarjetas** | Materias, importar PDFs, worker con texto + OCR, limpieza y corte, biblioteca con progreso, lista de tarjetas por documento, ver la página original. | Un PDF digital y uno escaneado tuyos quedan en tarjetas legibles. |
| **F2. Feed** | Pieles Hilo y Carrusel, "lo sé", guardar, marcar vistas, historias/filtro por materia. | Scrolleas tus apuntes en los dos estilos. |
| **F3. Intercepción** | Servicio de accesibilidad, aviso y consentimiento, pantalla interceptada, modos y pases. | Abrir Instagram muestra el Carrusel y abrir X muestra el Hilo. |
| **P1. Publicación (prueba)** | Clave de subida, política de privacidad, ficha, formularios de Play Console, declaración y video, prueba cerrada. | La prueba cerrada está abierta con 12+ testers. |
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
| OCR lento en documentos largos (~0,5–1,5 s por página). | Worker en segundo plano por tandas; las tarjetas aparecen a medida que se procesan. |
| Instagram o X cambian de paquete o de comportamiento. | Lista de paquetes configurable. |
| Android sigue restringiendo la accesibilidad (por ejemplo, la Protección avanzada de Android 17). | Detectar el servicio apagado y explicarlo; el feed dentro de la app sigue funcionando. |

---

## 14. Pendientes

- Probar la versión debug de F0 en la S23.
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
