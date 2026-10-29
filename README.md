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
- **Crear alimento** por 100 g o por porción.
- **Agregar rápido** (solo kcal y macros), **Copiar de ayer** por comida, **Recientes** con
  la última cantidad usada.
- **Objetivos**: sexo, edad, altura, peso, actividad, objetivo (bajar / mantener / ganar,
  con % de déficit o superávit) y distribución de macros (equilibrada, alta en proteína,
  baja en carbos, baja en grasas, keto o personalizada).
- **Progreso**: calorías de los últimos 7 días, promedio de macros y registro de peso.
- **Inglés o español**: sigue el idioma del teléfono, o se elige en **Perfil → Idioma**
  (Sistema / English / Español; en Android 13+ también desde Ajustes del sistema). Open
  Food Facts usa el nombre del producto en ese idioma y la base local tiene ambos nombres (buscás "pollo" o "chicken" y aparece igual).
  Lo ya registrado queda con el nombre que tenía.

Sin anuncios, sin cuentas, sin analítica, sin suscripción. Los datos quedan en el teléfono
(SQLite). Sin IA ni API keys: nada sale del teléfono salvo las consultas a Open Food Facts.
Permisos: solo internet; el lector de códigos de Google no necesita permiso de cámara. El APK
pesa **~3 MB** contra los 144 MB de Fitia.

## Cómo compilar

Requisitos (ya presentes en esta máquina): Android SDK `platform android-35` (en
`C:\Users\Thiago\AppData\Local\Android\Sdk`, referenciado por `local.properties`) y JDK 17.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
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
    OpenFoodFacts.kt       código de barras y búsqueda
    Goals.kt               BMR/TDEE, objetivo y distribución de macros
    FoodSearch.kt          búsqueda local sin tildes
    Prefs.kt               perfil e idioma
  ui/                      pantallas Compose
app/src/test/              tests (cálculos, búsqueda y parser de Open Food Facts)
```

## Estado

**Identidad:** paleta Oliva (`#5B7A1A` principal) en toda la app, en claro y oscuro; ícono del
launcher con el logo B tonal (tenedor, plato en tres porciones y cuchillo), fondo `#E4EBC8` y
figura `#3D5010`, con capa monocroma para los íconos temáticos de Android 13+.

Compila (debug y release minificado), pasan los 7 tests y lint queda sin advertencias. Se
quitaron la foto de comida, "Describir" y la lectura de etiquetas con IA (Claude), que
necesitaban una API key propia.

Probado en un Galaxy A55 (Android 16) por adb, en español e inglés y en claro y oscuro:
búsqueda local y en Open Food Facts, agregar, editar, borrar, agregar rápido, copiar de
ayer, Perfil y Progreso. Falta probar en el teléfono el escáner de códigos.
