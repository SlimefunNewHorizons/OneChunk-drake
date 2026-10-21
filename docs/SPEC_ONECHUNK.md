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

## Diseño confirmado por Jack (2026-10-21, tarde) — opción C

El jugador **mina hacia abajo / a través del chunk**, no rompe un punto fijo que respawnea infinito. No es un bucle.

1. **Primer chunk (bioma inicial) con recursos GARANTIZADOS, no aleatorios**: varios árboles, hierro, piedra, tierra.
   Debe sentirse como un trozo de mundo real, no una lista abstracta de bloques.
2. El jugador mina su contenido real. Cuando el chunk se agota/termina su progreso, **NO se reinicia igual**:
   aparece un **chunk nuevo con un bioma distinto** (ver §Rotación de biomas).
3. **Cada X chunks** (umbral a definir, p. ej. cada 3-5), el bioma cambia — más variedad y dificultad progresiva.
4. **Mobs**: aparecen según el bioma/progreso, cada vez más peligrosos a medida que se avanza.

### Por qué usar Blueprints de BentoBox, no generación 100% aleatoria
BentoBox ya trae su propio sistema de planos (`.blu`, integrado con WorldEdit) — hoy `ChunkBlock` lo usa solo para
la plataforma de bedrock inicial (`blueprints/bedrock.blu`, 239 bytes). Es la herramienta correcta para GARANTIZAR
contenido (árboles, menas, tierra) sin azar: se autoría un plano real por bioma (en el propio servidor con
WorldEdit, `//copy` + `//schematic save`, o construido a mano) y se pega en la huella del chunk (`BlueprintsManager`
de BentoBox) al empezar cada fase/bioma. Paper también trae generación nativa de árboles
(`World#generateTree(Location, TreeType)`) y vetas de mena se pueden sembrar con el propio API de bloques — útiles
como variedad DENTRO de un plano base, nunca como único mecanismo (porque "obligatoriamente" = garantizado, no al azar).

### Rotación de biomas y mobs
Reutilizar el sistema de fases de AOneBlock (`oneblocks/OneBlockPhase.java`, `OneBlocksManager`) tal cual, pero
cambiando su efecto: en vez de "siguiente ítem en el punto fijo", cada fase define **qué plano de bioma pegar** y
**qué tabla de mobs habilitar** (spawn de biomas vanilla reales por bioma objetivo, o config propia en `config.yml`
como ya hace AOneBlock con `MagicBlockEntityEvent`). El contador de bloques rotos (`OneBlockIslands`) sigue siendo
la métrica de progreso que decide cuándo toca la siguiente fase/bioma.

### Plan de implementación (en orden, un paso por pasada, con prueba y compilación antes de avanzar)
1. **Planos**: crear `blueprints/biome-<nombre>.blu` para 3-4 biomas iniciales (bosque, desierto, nieve, ¿cueva/
   nether?), con WorldEdit en staging — cada uno con recursos garantizados acordes al bioma.
2. **Detección**: cambiar los 7 sitios de `l.equals(i.getCenter())` en `BlockListener.java` por
   `island.inIslandSpace(l)` (API nativa de BentoBox, ya confirmada) para que minar en cualquier punto de la huella
   del chunk cuente como progreso.
3. **Colocación/progreso**: sustituir `processNextBlock` (que hoy coloca UN bloque en el centro) por: contar
   bloques minados -> al llegar al umbral de fase, pegar el plano de bioma correspondiente con `BlueprintsManager`
   en la huella del chunk, y activar la tabla de mobs de esa fase.
4. **Mobs**: configurar spawns por fase (vanilla por bioma, o `EntitySpawnEvent` propio como ya usa el addon).
5. **Probar en staging con jugador real** antes de acercarse a Dallas: ver §Smoke test en `TEMPORADA2_STAGING.md`.

Paso 2 es independiente y seguro de hacer ya (no depende de los planos); pasos 1, 3 y 4 van juntos porque sin
planos reales la colocación no tiene qué pegar.
