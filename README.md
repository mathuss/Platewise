# Platewise

(Nombre de la app en el celular; el proyecto se llama CountMyMacros.)

App Android liviana y **sin anuncios** para contar **calorías, proteínas, carbohidratos y
grasas**. Toma las funciones principales de *Fitia*, reconstruidas a partir del análisis
del APK original ([docs/ANALISIS_FITIA.md](docs/ANALISIS_FITIA.md)).

- **Diario** por día con las 5 comidas de Fitia (Desayuno, Media mañana, Almuerzo,
  Merienda, Cena): anillo de calorías restantes y barras de P / C / G contra tu objetivo.
- **Código de barras**: escaneo con el lector de Google y datos de Open Food Facts. Si el
  producto no está, lo creás a mano y queda guardado.
- **Buscador**: base local de ~135 alimentos comunes (al instante, sin internet) + búsqueda
  en Open Food Facts. Acepta tildes o no y nombres alternativos (palta/aguacate, etc.).
- **Describir**: "2 empanadas de carne y una coca chica" → alimentos de la base local, con
  cantidad y porción reconocidas y revisables antes de agregar. Reglas de texto, no IA: anda
  bien con frases directas, no con descripciones abiertas ("un poco de queso arriba").
- **Crear alimento** por 100 g o por porción, o **escaneando la etiqueta nutricional**: lee el
  texto de la foto con ML Kit, en el teléfono (sin key, sin costo, sin salir de internet), y
  completa kcal/P/C/G. Anda bien con etiquetas nítidas y de frente; con fotos borrosas o
  tablas poco comunes no completa nada y avisa.
- **Agregar rápido** (solo kcal y macros), **Copiar de ayer** por comida, **Recientes** con
  la última cantidad usada.
- **Objetivos**: sexo, edad, altura, peso, actividad, objetivo (bajar / mantener / ganar,
  con % de déficit o superávit) y distribución de macros (equilibrada, alta en proteína,
  baja en carbos, baja en grasas, keto o personalizada).
- **Progreso**: calorías de los últimos 7 días, promedio de macros y registro de peso.
- **Entrenar**: rutinas con ejercicios, series (normal, calentamiento, drop set, al fallo) y
  descanso; entrenamiento en vivo con cronómetro y temporizador de descanso, o registrado
  después con fecha y duración. Catálogo de 454 ejercicios en inglés y español
  ([docs/ANALISIS_HEVY.md](docs/ANALISIS_HEVY.md)). Las calorías quemadas (MET 5,0 × tu peso
  × horas) se muestran en el Diario como dato; el objetivo diario sigue dependiendo del nivel
  de actividad, que ya cuenta tu ejercicio semanal.
- **Inglés o español**: sigue el idioma del teléfono, o se elige en **Perfil → Idioma**
  (Sistema / English / Español; en Android 13+ también desde Ajustes del sistema). Open
  Food Facts usa el nombre del producto en ese idioma y la base local tiene ambos nombres (buscás "pollo" o "chicken" y aparece igual).
  Lo ya registrado queda con el nombre que tenía.

Sin anuncios, sin cuentas, sin analítica, sin suscripción. Los datos quedan en el teléfono
(SQLite). Sin IA en la nube ni API keys: nada sale del teléfono salvo las consultas a Open
Food Facts; describir y leer etiquetas corren enteros en el dispositivo. Permisos: solo
internet; ni cámara ni almacenamiento, porque las fotos pasan por la app de cámara y el
selector de fotos del sistema. El APK pesa **~3,8 MB** contra los 144 MB de Fitia.

## Cómo compilar

Requisitos (ya presentes en esta máquina): Android SDK `platform android-35` (en
`C:\Users\Thiago\AppData\Local\Android\Sdk`, referenciado por `local.properties`) y JDK 17.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
.\gradlew.bat :app:testDebugUnitTest   # tests
.\gradlew.bat :app:assembleRelease     # APK minificado → app/build/outputs/apk/release/
```

Sin `signing/keystore.properties`, el release se firma con la clave debug: sirve para
instalarlo en tu teléfono, no para publicarlo. Para una clave propia, creá
`signing/keystore.properties` con `storeFile`, `storePassword`, `keyAlias` y `keyPassword`
(la carpeta está en `.gitignore`).

Instalar con el teléfono conectado por USB (depuración activada):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\Platewise-1.0.apk
```

## Datos y fuentes

- **Base local** (`app/src/main/assets/alimentos.json`): valores de referencia por 100 g,
  mayormente de USDA FoodData Central; los platos argentinos (milanesa, empanada, tarta…)
  son promedios aproximados. Podés crear tus propios alimentos con los valores exactos.
- **Open Food Facts**: productos envasados, base abierta (ODbL). La búsqueda por texto
  tiene límite de uso, por eso se busca ahí solo cuando tocás buscar. Los productos que se
  venden en tu país (según la región del teléfono) aparecen primero.
- **No** incluye la base de alimentos de Fitia: está en sus servidores (no en el APK) y
  usar su API privada no corresponde.
- Calorías objetivo con **Mifflin-St Jeor** × factor de actividad. La fórmula exacta de
  Fitia no se pudo recuperar del APK.

## Estructura

```
app/src/main/res/values/strings.xml      textos en inglés (idioma por defecto)
app/src/main/res/values-es/strings.xml   textos en español
app/src/main/java/com/countmymacros/
  MainActivity.kt          pestañas (Diario, Progreso, Perfil) + navegación
  data/
    Models.kt              Food, Serving, Entry, Macros, Meal
    Db.kt                  SQLite: entradas, mis alimentos, peso
    Repo.kt                base local + DB + Open Food Facts
    BaseFoods.kt           parser de assets/alimentos.json
    OpenFoodFacts.kt       código de barras y búsqueda
    MealText.kt            "Describir": texto libre → alimentos de la base (sin IA)
    LabelText.kt           etiqueta nutricional: líneas de OCR → kcal/P/C/G (sin IA)
    Goals.kt               BMR/TDEE, objetivo y distribución de macros
    FoodSearch.kt          búsqueda local sin tildes
    Prefs.kt               perfil e idioma
  ui/                      pantallas Compose (LabelScanner.kt: ML Kit → líneas de OCR)
app/src/test/              tests (cálculos, búsqueda, Describir, lectura de etiquetas y
                           parser de Open Food Facts)
```

## Estado

**Identidad:** paleta Oliva (`#5B7A1A` principal) en toda la app, en claro y oscuro; ícono del
launcher con el logo A tonal (tenedor, plato en tres porciones y cuchillo, con cubiertos a escala real, ~0,7 del plato), fondo `#E4EBC8` y
figura `#3D5010`, con capa monocroma para los íconos temáticos de Android 13+.

Compila (debug y release minificado), pasan los 9 tests y lint queda sin advertencias. Se
quitaron la foto de comida (requiere reconocer el plato, no hay forma de hacerlo sin IA) y
la IA en la nube (Claude) que antes impulsaba "Describir" y la lectura de etiquetas; las dos
vuelven con reglas de texto y ML Kit, corriendo en el teléfono.

Probado en un Galaxy A55 (Android 16) por adb, en español e inglés y en claro y oscuro:
búsqueda local y en Open Food Facts, agregar, editar, borrar, agregar rápido, copiar de
ayer, Perfil y Progreso. Falta probar en el teléfono "Describir", la lectura de etiquetas y
el escáner de códigos.
