# Platewise

(Nombre de la app en el celular; el proyecto se llama CountMyMacros.)

App Android liviana y **sin anuncios** para contar **calorías, proteínas, carbohidratos y
grasas**. Toma las funciones principales de *Fitia*, reconstruidas a partir del análisis
del APK original ([docs/ANALISIS_FITIA.md](docs/ANALISIS_FITIA.md)).

- **Diario** por día con las 5 comidas de Fitia (Desayuno, Media mañana, Almuerzo,
  Merienda, Cena): anillo de calorías restantes y barras de P / C / G contra tu objetivo.
- **Foto de comida**: le sacás una foto al plato (o elegís una de la galería) y la IA
  separa cada alimento, estima los gramos y calcula kcal y macros. Revisás, corregís los
  gramos (los macros se recalculan) y lo agregás. Si algo está mal, le escribís una nota
  ("era media porción", "sin aceite") y reanaliza.
- **Describir**: "2 empanadas de carne y una coca chica" → alimentos con macros.
- **Código de barras**: escaneo con el lector de Google y datos de Open Food Facts. Si el
  producto no está, lo creás (a mano o leyendo la etiqueta con IA) y queda guardado.
- **Buscador**: base local de ~135 alimentos comunes (al instante, sin internet) + búsqueda
  en Open Food Facts. Acepta tildes o no y nombres alternativos (palta/aguacate, etc.).
- **Crear alimento** por 100 g o por porción, o **escaneando la etiqueta nutricional**.
- **Agregar rápido** (solo kcal y macros), **Copiar de ayer** por comida, **Recientes** con
  la última cantidad usada.
- **Objetivos**: sexo, edad, altura, peso, actividad, objetivo (bajar / mantener / ganar,
  con % de déficit o superávit) y distribución de macros (equilibrada, alta en proteína,
  baja en carbos, baja en grasas, keto o personalizada).
- **Progreso**: calorías de los últimos 7 días, promedio de macros y registro de peso.
- **Inglés o español**: sigue el idioma del teléfono, o se elige en **Perfil → Idioma**
  (Sistema / English / Español; en Android 13+ también desde Ajustes del sistema). La IA
  nombra los alimentos en ese idioma, Open Food Facts usa el nombre del producto en ese
  idioma y la base local tiene ambos nombres (buscás "pollo" o "chicken" y aparece igual).
  Lo ya registrado queda con el nombre que tenía.

Sin anuncios, sin cuentas, sin analítica, sin suscripción. Los datos quedan en el teléfono
(SQLite). Permisos: solo internet; ni cámara ni almacenamiento, porque las fotos pasan por
la app de cámara y el selector de fotos del sistema. El APK pesa **~9 MB** contra los
144 MB de Fitia.

## La IA (foto, descripción y etiquetas)

Usa **Claude** (`claude-opus-5-5`) con tu propia API key de Anthropic:

1. Creá una key en <https://console.anthropic.com/settings/keys> y cargale crédito.
2. En la app: **Perfil → Inteligencia artificial → pegar la key → Guardar**.

Cada análisis cuesta unos centavos de dólar (estimado: 2–5 ¢ por foto) y se cobra en tu
cuenta. La key se guarda solo en el teléfono y se excluye de los backups en la nube.
Las solicitudes van con *fallback* del lado del servidor (`fallbacks: "default"`): si un
filtro de seguridad rechazara una imagen, Anthropic la reintenta en otro modelo.
El buscador, el código de barras y todo lo demás funcionan sin key.

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
    FoodAi.kt              Claude: foto, texto y etiqueta → JSON con esquema
    OpenFoodFacts.kt       código de barras y búsqueda
    Goals.kt               BMR/TDEE, objetivo y distribución de macros
    FoodSearch.kt          búsqueda local sin tildes
    Prefs.kt               perfil y API key
  ui/                      pantallas Compose
app/src/test/              tests (cálculos, búsqueda, parsers y el request a Claude
                           contra un servidor local, sin gastar crédito)
```

## Estado

**Identidad:** paleta Oliva (`#5B7A1A` principal) en toda la app, en claro y oscuro; ícono del
launcher con el logo B tonal (tenedor, plato en tres porciones y cuchillo), fondo `#E4EBC8` y
figura `#3D5010`, con capa monocroma para los íconos temáticos de Android 13+.

Compila (debug y release minificado), pasan los 12 tests y lint queda sin advertencias. El
request a Claude se probó de punta a punta con el SDK real contra un servidor local.

Probado en un Galaxy A55 (Android 16) por adb, en la versión solo en español: búsqueda
local y en Open Food Facts, agregar, editar, agregar rápido, copiar de ayer y el aviso de
API key faltante. Falta probar en el teléfono: la versión bilingüe, Perfil, Progreso, el
escáner de códigos y la foto con IA.
