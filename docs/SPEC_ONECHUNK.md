# Especificación — OneChunk (fork de AOneBlock)

**Diseño pedido por Jack (2026-10-21):** igual que OneBlock, pero en vez de un bloque único, **una columna de chunk
entera** (16×16 de huella, de `minHeight` a `maxHeight`) que se regenera y progresa por fases igual que el bloque
mágico de OneBlock. El addon `ChunkBlock` que estaba instalado en Dallas (oficial de BentoBox, mismo autor) **no es
esto**: desbloquea chunks vecinos al subir de nivel con un bloque mágico puntual — un diseño distinto, no el pedido.
Investigación completa en el ticket #401 de SAORI (`ai-hub`, `saori_orchestrador.py`).

## Mecanismo real de AOneBlock (investigado en el código fuente, 2026-10-21)
- El generador de mundo (`generators/ChunkGeneratorWorld.java`) **no coloca nada**: devuelve chunks vacíos. Todo el
  mecanismo es por eventos, no por generación de terreno. Esto facilita el fork: no hay que tocar la generación.
- El núcleo vive en `listeners/BlockListener.java` (1102 líneas). Prácticamente todo compara contra
  `island.getCenter()`, un único `Location` (X,Y,Z fijos):
  - Detección de rotura del bloque mágico: `l.equals(i.getCenter())`.
  - Colocación del siguiente bloque de la fase: `island.getCenter().getBlock().setType(...)`.
  - Holograma, partículas, sonido de aviso, drop de cofres/loot, protección del bloque, generación de entidades:
    todos anclados al mismo punto.
- `oneblocks/OneBlockPhase.java` / `OneBlocksManager.java`: progresión de fases por **número total de bloques rotos**
  (contador en `OneBlockIslands`, dataobject por isla) — este contador se puede REUSAR tal cual para una columna
  (cada bloque roto de la columna avanza el mismo contador), el cambio real es solo DÓNDE se coloca/detecta.

## Qué hay que cambiar (alcance real, para trabajar en varias pasadas con pruebas)
1. **Detección de rotura**: sustituir `l.equals(i.getCenter())` por "¿`l` está dentro de la huella X,Z del chunk de
   la isla, en cualquier Y entre minHeight y maxHeight?" (comparación por rango, no por igualdad exacta).
2. **Colocación del siguiente bloque**: en vez de SIEMPRE el centro, elegir una posición Y libre dentro de la
   columna (de abajo hacia arriba, o la primera posición de aire sobre lo ya generado) para que la isla se rellene
   como una columna sólida, no un punto que salta de bloque en bloque en el mismo sitio.
3. **Holograma / partículas / sonido de aviso**: deben seguir la posición ACTUAL dentro de la columna, no un punto
   fijo — adaptar para que apunten a la cima de lo generado hasta ahora.
4. **Protección y límites**: `BlockProtect.java`, `StartSafetyListener.java`, `MakeSpace.java` — revisar que
   trabajen sobre todo el volumen de la columna, no solo el punto central.
5. **Mantener intactos**: el sistema de fases (`oneblocks/`), el contador de bloques rotos, el panel de fases, los
   comandos de isla/admin, las recompensas — toda la progresión y el contenido de fases es reutilizable sin cambios.

## Reglas (igual que el resto de la org)
- Nunca romper IDs de ítem ni datos de jugadores existentes si algún día se despliega sobre islas ya creadas.
- Probar primero en staging de Temporada 2 (26.2), nunca directo en Dallas.
- Licencia EPL-2.0 se mantiene íntegra; créditos a tastybento/BentoBoxWorld en el README.
- Compilar siempre con `~/ai-hub/scripts/build_seguro.sh`.
- Un cambio por pasada, con prueba, documentado en `docs/proactivo/BITACORA.md`.

## Decisión de diseño pendiente (Jack, preguntada 2026-10-21): qué pasa al llenar la columna
El mecanismo original reutiliza SIEMPRE el mismo punto (rompes, aparece otro bloque en el mismo sitio, infinito).
Una columna real tiene altura finita (~320 bloques en 1.21+). **No seguir implementando `processNextBlock` /
`processHologramAndWarnings` / el cálculo de la siguiente posición hasta tener esta respuesta** — adivinar aquí es
exactamente el error que creó `ChunkBlock` (alguien eligió un diseño sin confirmarlo).

Opciones planteadas:
- **A) Bucle**: al llenar la columna, se recicla desde abajo (como el bloque único, pero en bucle de altura).
- **B) Mina hacia abajo**: la columna existe entera desde el inicio; se cava hacia el fondo, mina vertical infinita.
- **C)** Otra cosa.

Hallazgo útil mientras tanto: la API de BentoBox ya trae `island.inIslandSpace(Location)` (huella X,Z, cualquier Y)
y `island.onIsland(Location)` — sirven tal cual para la detección de "rotura dentro de la columna" en los 7 sitios
de `BlockListener.java` que hoy comparan `l.equals(i.getCenter())`. El `process()` central re-lee SIEMPRE el bloque
en `island.getCenter()` ignorando dónde rompió el jugador, así que detección y colocación están desacopladas — se
puede cambiar la detección primero sin romper nada, pero la colocación (dónde aparece el siguiente bloque) necesita
la decisión de arriba antes de tocarse.
