# Análisis de Hevy (ingeniería inversa del APK)

Qué hay adentro de `Hevy.apk` y qué se tomó para la pestaña **Entrenar** de Platewise. Todo sale
de análisis **estático** (no se ejecutó la app ni se capturó tráfico). Se marca qué es
**observado**, qué es **inferido** y qué quedó **sin resolver**.

## Identidad del artefacto

| | |
|---|---|
| Tamaño | 127 MB (3.863 archivos) |
| Stack | **React Native + Hermes**: la lógica está en `assets/index.android.bundle` (22,6 MB, bytecode Hermes **v96**, 62.771 funciones, 143.478 strings). Hay 9 DEX, pero son el framework y librerías nativas, no la lógica de la app |
| Herramientas | JADX no sirve (solo ve el framework). Se decompiló el bundle con `hermes-dec` 0.1.8 (`hbc-decompiler`) a ~89 MB de pseudo-JS. El APK no se ejecutó |

## Biblioteca de ejercicios (observado)

- **454 ejercicios** incrustados en el bundle como objetos literales (ids únicos de 8 hex, p. ej.
  `79D0BB3A` = "Bench Press (Barbell)"). Campos: `id`, `title` (+ `es_title`, `de_title`, `fr_title`,
  … 17 idiomas), `priority` (0–10, orden por popularidad), `muscle_group`, `exercise_type`,
  `equipment_category`, `is_custom`, `thumbnail_url`, `thumbnail_url_female`, `url` /
  `url_female` (video, en servidores de Hevy; **no** está en el APK), `manual_tag` (alias de
  búsqueda), `volume_doubling_supported`, `hundred_percent_bodyweight_exercise`.
- `other_muscles` es `null` en los 454: **no hay músculos secundarios** en el catálogo.
- **Tipos** (cantidad): `weight_reps` 288, `reps_only` 104, `duration` 24, `distance_duration` 16,
  `bodyweight_reps` 10, `bodyweight_assisted_reps` 5, `short_distance_weight` 5,
  `floors_duration` 1, `steps_duration` 1. (`weight_duration` existe en el código pero ningún
  ejercicio incluido lo usa.)
- **Músculos** (20): quadriceps 55, chest 49, full_body 47, abdominals 46, shoulders 43, biceps 33,
  glutes 29, triceps 27, upper_back 26, lats 26, cardio 25, hamstrings 12, calves 12, forearms 6,
  traps 5, lower_back 4, neck 4, abductors 2, adductors 2, other 1.
- **Equipo** (9): machine 146, none 105, barbell 75, dumbbell 70, other 17, resistance_band 13,
  kettlebell 13, plate 8, suspension 7.
- **Imágenes:** 870 miniaturas JPG (10,5 MB) en `res/drawable-xxhdpi-v4/src_exercises_assets_*.jpg`,
  una versión masculina y otra femenina por ejercicio. El nombre del recurso es el de la URL
  normalizado (minúsculas, sin `-`, `(`, `)`, `@3x`), lo que permite emparejarlas con cada
  ejercicio: 902 de 908 miniaturas esperadas (algunos ejercicios comparten imagen). Faltan las
  dos de Climbing y Snowboarding y la femenina de Kettlebell Turkish Get Up y Power Snatch. Las imágenes tienen derechos del proveedor
  de Hevy; el usuario indicó que no tienen copyright, pero **no se suben al repo**
  (`tools/hevy_extract.py` las extrae del APK local).

## Entrenamientos y rutinas (observado: nombres de campos y claves en el bundle)

- **Serie:** `set_type` ∈ `normal`, `warmup`, `dropset`, `failure`; valores `weight_kg`, `reps`,
  `duration_seconds`, `distance_meters`, `rpe`, `custom_metric`; `previous_*` (los de la última
  vez, que se muestran al registrar); `set_completed`.
- **Ejercicio dentro de rutina/entrenamiento:** `exercise_template_id`, `rest_seconds` /
  `rest_timer_seconds`, `superset_id`, `exercise_notes`, lista de series.
- **Rutina:** `routine_id`, nombre, `routine_notes`, `routine_folder` (carpetas, orden en
  `routine_folder_order`), `parent_routine_id`.
- **Entrenamiento:** `workout_id`, `startTime`, `timeEvents` (pausas/reanudaciones, `type` =
  `pause` | `resume`), `volume_kg`, `user_bodyweight_kg`, `total_calories`.
- **Volumen:** Hevy ofrece "incluir series de calentamiento" (`volume_includes_warmup_sets`) y
  "duplicar volumen" para ejercicios de pesas por lado (`volume_doubling_*`).

## Calorías quemadas (observado + inferido)

- **Observado:** la función `estimateWorkoutCalories(d)` devuelve **`d / 60 × 5,6`**. Se la llama
  con `calculateDuration(workout, ahora)`, que resta timestamps unix (segundos) y descuenta las
  pausas de `timeEvents`. Es decir, **5,6 kcal por minuto de entrenamiento**, sin mirar peso
  corporal, ejercicios ni series. Solo se usa para escribir `total_calories` en Health Connect /
  Apple Health; si hay reloj (WearOS / Apple Watch), las calorías vienen del reloj.
- **Inferido:** 5,6 kcal/min ≈ MET 5,0 × ~67 kg. Platewise usa la misma forma (duración × MET 5,0)
  pero con el **peso real del usuario**: `kcal = 5,0 × peso_kg × horas`.

## Lo que se tomó y lo que no

- **Se tomó:** catálogo de ejercicios (nombres en/es, tipo, músculo, equipo) y miniaturas;
  tipos de serie; rutinas con ejercicios, series, descanso y notas; registro en vivo o a
  posteriori; estimación de calorías por duración.
- **No se tomó:** social (feed, likes, comentarios), carpetas, entrenadores, rankings y
  "strength level", sincronización con servidores, videos, relojes, Health Connect.

## Sin resolver

- Los textos de instrucciones de cada ejercicio (están en el bundle pero no se usan).
- El algoritmo exacto de volumen con `volume_doubling`.
