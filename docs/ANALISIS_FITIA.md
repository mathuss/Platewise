# Análisis de Fitia (ingeniería inversa del APK)

Qué hay adentro de `Fitia.apk` y qué se tomó para CountMyMacros. Todo sale de análisis
**estático** del APK (no se ejecutó la app ni se capturó tráfico). Se marca qué es
**observado** (está en el APK), qué es **inferido** y qué quedó **sin resolver**.

## Identidad del artefacto

| | |
|---|---|
| Paquete | `com.nutrition.technologies.Fitia` |
| Versión | 26.2.1 (versionCode 1520), targetSdk 36 |
| SHA-256 | `5b0a2d0751a7c9a9fa100c6bb603b15f01af75214816a43df9fbc3ebd62bd701` |
| Tamaño | 144,5 MB (103 MB de `res/`: imágenes por idioma y modo oscuro) |
| Stack | Kotlin 2.0.21 nativo (Compose + Views), Gradle 8.11.1, Room, Retrofit, Hilt. **No** es Flutter ni React Native |
| Código | 6 DEX + `assets/audience_network.dex` (cargado dinámicamente); ofuscado con R8 salvo la capa de datos |

Evidencia REA: `ev_ba170d39…` (no hay `libapp.so` → no Flutter), `ev_3df98f38…`
(inventario completo: 4.114 entradas), `ev_a26a6016…` (extracción verificada). Las
clases, campos, strings y esquemas SQL salen de un parser de DEX propio sobre esa
extracción (JADX no estaba instalado, así que no hay código decompilado).

## Lo que importa para contar macros

### Modelo de datos (observado: sentencias `CREATE TABLE` de Room en el DEX)

- **Nutrientes por gramo.** Los alimentos guardan `caloriesPerGram`, `proteinPerGram`,
  `carbsPerGram`, `fatPerGram` (+ fibra, sodio, azúcares, grasas sat./trans), y una lista
  de **porciones** `{name, quantity, size (g), unit}`. Un registro = gramos × valor por gramo.
  CountMyMacros hace lo mismo con valores cada 100 g.
- **Día → comidas → ítems.** `DailyRecordModel` (objetivo kcal/P/C/G del día) →
  `MealModel` (5 comidas: breakfast, midMorning, lunch, midAfternoon, dinner; cada una con
  objetivo y límites) → `FoodModel` / `RecipeModel` / `QuickItemModel` (este último: nombre +
  kcal/P/C/G sueltos, sin alimento detrás).
- **Objetivos** (`PreferencesModel`): `baseCalories`, `calorieDeficitPercentage`,
  `macrosDistributionType`, `caloriesGoal`, `proteinsGoal`, `carbsGoal`, `fatsGoal`,
  `phyisicalActivityLevel`, ajuste automático de calorías. Tipos de distribución vistos:
  `BALANCED`, `HIGH_PROTEIN`, `LOW_CARB`, `LOW_FAT`, `KETO`, `MEDITERRANEAN`, `ZONE`, `CUSTOM`.
- Peso (`WeightModel`), medidas, ejercicio (MET), ayuno, agua, suplementos, lista de compras.

### Base de alimentos (observado: **no** está en el APK)

La base de alimentos de Fitia vive en sus servidores: búsqueda en
`https://elastic.fitia.app/` (modelos `FoodElasticV2`, `RecipeElasticV2`), Algolia
(`AlgoliaServices`), Firestore y `searchProductsByBarcodeAndCountryV2` para códigos de
barras. El APK solo trae un ejemplo de plan (`assets/both.json`) y sinónimos de nombres
(`Words.json`). Copiarla implicaría usar la API privada y autenticada de Fitia, cosa
que este proyecto no hace. En su lugar usa Open Food Facts (abierta) + una base local
de referencia + estimación con IA.

### Foto de comida → nutrientes (observado + inferido)

- Cliente: `FitiaScannerApi.scanPhoto` / `scanText`. Servicio:
  `https://scanner-436444335773.us-central1.run.app` con rutas `/api/v1/photo`,
  `/api/v1/text`, `/api/v2/scan/photo`, `/api/v2/scan/photo/improve`, `/api/v2/scan/voice`.
- La foto se manda al backend; la respuesta (`ScannerPhotoResponse`) es una "receta" con
  ingredientes y nutrientes (`RecipeElasticV2`). El backend tokeniza cada alimento como
  `ScannerEdibleToken {name, quantity, unit, weight, cookingState}` y lo empareja contra su
  base (`ScannerMealResponse {search, found, item, servings}`).
- Los nutrientes llevan procedencia: `NutrientSource = OFFICIAL | ESTIMATED | MATCH | ORIGINAL`.
- **Inferido:** el reconocimiento lo hace un modelo de visión del lado del servidor (hay
  strings `geminiMessages`/`geminiAction` en otras funciones). **Sin resolver:** qué modelo
  usa el scanner y cómo calcula las porciones; eso no está en el APK.
- Otras variantes: etiqueta nutricional (`ScanFoodNutritionLabelWithImage`), frente del
  paquete (`ScanFoodFrontPackageWithImage`), texto y voz ("Senku").

CountMyMacros reemplaza ese backend con Claude (con la API key del usuario): la misma idea
de alimentos separados con gramos y estado de cocción, pero el modelo estima los
nutrientes directamente y el usuario revisa y corrige antes de guardar.

### Código de barras (observado)

ML Kit barcode bundled (`assets/mlkit_barcode_models/*.tflite`, ~0,9 MB) + búsqueda por
código y país en el backend. CountMyMacros usa el escáner de Google Play services (sin
permiso de cámara, sin modelos dentro del APK) + Open Food Facts.

## Publicidad, rastreo y pagos (observado)

- **Anuncios:** Facebook Audience Network (`com.facebook.ads`, ~2.600 clases, más un DEX
  propio de 3,4 MB en assets). Permisos `AD_ID` y `ACCESS_ADSERVICES_*`.
- **Medición:** Firebase Analytics/Crashlytics/Performance, Google Tag Manager, TikTok SDK,
  Install Referrer. Atribución con Singular inferida de `DeepLinkSingular*` y `fitia.sng.link`
  (las clases de la librería están ofuscadas).
- **Suscripción:** Google Play Billing + Qonversion.
- **51 permisos declarados**, entre ellos ubicación, micrófono, cámara, cuentas, Health
  Connect (lectura/escritura), alarmas exactas e inicio al encender.

CountMyMacros: ningún SDK de anuncios, analítica ni billing; permiso `INTERNET` y nada más
propio.

## Sin resolver

- Fórmula exacta de calorías objetivo de Fitia (está en código ofuscado que no se
  decompiló). CountMyMacros usa Mifflin-St Jeor con factores de actividad estándar.
- Porcentajes exactos de cada distribución de macros de Fitia. Los de CountMyMacros son
  valores habituales, documentados en `Goals.kt`.
- Comportamiento en ejecución (no se instaló ni se observó la app).

## Mapa Fitia → CountMyMacros

| Fitia | CountMyMacros |
|---|---|
| Diario con 5 comidas y objetivos | Igual (Desayuno, Media mañana, Almuerzo, Merienda, Cena) |
| Buscador (Elastic/Algolia, privado) | Base local + Open Food Facts |
| Código de barras (ML Kit + backend) | Google code scanner + Open Food Facts |
| Foto de comida (scanner propio) | Claude con tu API key |
| Texto / voz → alimentos ("Senku") | Texto → alimentos con Claude |
| Escanear etiqueta nutricional | Igual, con Claude |
| Agregar rápido (QuickItem) | Igual |
| Copiar de ayer | Igual, por comida |
| Crear alimento | Igual |
| Peso | Igual (actualiza el objetivo) |
| Objetivos BMR/TDEE/déficit/macros | Igual (Mifflin-St Jeor) |
| Planificador de comidas, coach IA, social, familia, ayuno, Health Connect | Fuera de alcance |
| Anuncios, analítica, suscripción | No |
