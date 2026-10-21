# NOTICE — Origen de este repositorio

`OneChunk-drake` es un **fork** de [`BentoBoxWorld/AOneBlock`](https://github.com/BentoBoxWorld/AOneBlock),
obra original de **tastybento** y la comunidad de BentoBox, bajo **Eclipse Public License 2.0** (ver `LICENSE`).

**No somos los autores originales.** Todo el motor base (BentoBox, el gestor de islas, el sistema de fases y el
generador de mundo) sigue siendo de BentoBoxWorld; este fork lo adapta para un gamemode distinto, conservando la
licencia EPL-2.0 íntegra.

## Qué cambia respecto al original
AOneBlock regenera **un único bloque** por isla (`island.getCenter()`, un punto XYZ fijo) que progresa por fases al
romperlo. `OneChunk-drake` adapta ese mismo motor para que la progresión sea sobre **una columna completa del chunk
de la isla** (16×16, de la altura mínima construible al cielo), no un solo punto — ver `docs/SPEC_ONECHUNK.md`.

## Créditos
- Autor original y mantenedor de BentoBox/AOneBlock: **tastybento** (github.com/tastybento).
- Comunidad BentoBoxWorld: https://bentobox.world
