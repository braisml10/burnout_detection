# Burnout Detection App
 
Aplicación Android para la monitorización de patrones de uso del smartphone como indicadores de burnout digital.
 
Trabajo de Fin de Grado — Grado en Ingeniería Informática
Escola Superior de Enxeñaría Informática, Universidade de Vigo
 
Autor: Brais Mondragón López

Tutor: Iván Rodríguez Conde 

Co-tutora: Rosalía Laza Fidalgo
 
## Descripción
 
Burnout Detection monitoriza de forma pasiva el comportamiento digital del usuario (tiempo de pantalla, cambios entre aplicaciones, notificaciones, llamadas y SMS) y, a partir de esos datos, calcula diariamente un índice de riesgo de burnout digital mediante un motor de reglas deterministas.
 
Todo el procesamiento (captura, agregación y cálculo del riesgo) se realiza íntegramente en el dispositivo. La aplicación no solicita el permiso `INTERNET` y solo almacena metadatos de interacción, nunca el contenido de mensajes, llamadas o notificaciones.

## Características principales
 
- Captura en segundo plano de tiempo de pantalla, desbloqueos, cambios entre apps y notificaciones mediante `UsageStatsManager` y `NotificationListenerService`.
- Métricas de comunicación (llamadas y SMS) a partir de los `ContentProvider` nativos de Android, sin acceder al contenido.
- Motor de riesgo de burnout (`BurnoutRiskEngine`): puntuación de 0 a 2 calculada como suma ponderada de cinco dimensiones (tiempo de pantalla, fragmentación, uso nocturno, presión de notificaciones y desviación de tendencia respecto al histórico de 7 días).
- Procesamiento periódico mediante `WorkManager` (`DailyAggregationWorker`), con latencia máxima de aproximadamente una hora.
- Paneles visuales con KPIs y gráficas (uso diario/horario, notificaciones, comunicaciones, tendencia de riesgo a 7 días) mediante MPAndroidChart.
- Perfil de usuario local, con registro/inicio de sesión y contraseñas almacenadas como hash SHA-256.
- Interfaz disponible en español, gallego e inglés.
- Retención automática de datos: eventos y métricas se eliminan a los 7 días.

## Stack tecnológico
 
| Componente | Tecnología |
|---|---|
| Lenguaje | Java |
| Persistencia | Room (SQLite) |
| Tareas en segundo plano | WorkManager |
| Gráficas | MPAndroidChart |
| Build | Gradle 8.11.1, Android Gradle Plugin 8.9.2 |
| Tests | JUnit 4, Espresso, AndroidX Test |
| SDK mínimo | Android 7.0 (API 24) |
 
## Permisos utilizados
 
| Permiso | Uso |
|---|---|
| `PACKAGE_USAGE_STATS` | Tiempo de pantalla, apps en primer plano, cambios de app |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Metadatos de notificaciones recibidas |
| `READ_CALL_LOG` | Fecha y duración de llamadas |
| `READ_SMS` | Fecha de SMS recibidos (no el contenido) |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE` | Soporte para la ejecución periódica en segundo plano |
 
La aplicación no solicita el permiso `INTERNET`: todo el procesamiento es local.
 
## Getting Started
 
### Requisitos previos
 
- Android Studio (recomendado: Meerkat 2024.3.1 o superior).
- JDK 17 (incluido con Android Studio).
- Dispositivo Android físico con Android 7.0+ o emulador con API 24+. Se recomienda un dispositivo físico para probar permisos especiales como el acceso a notificaciones o a estadísticas de uso.
### 1. Clonar el repositorio
 
```bash
git clone <url-del-repositorio>
cd burnout_app
```
 
### 2. Abrir el proyecto
 
Abre Android Studio, selecciona **Open** y elige la carpeta raíz del proyecto. El wrapper de Gradle (`gradlew`) descargará automáticamente la versión necesaria (8.11.1) y sincronizará las dependencias.
 
### 3. Compilar y ejecutar
 
Desde Android Studio, pulsa **Run** sobre el módulo `app`, o desde la terminal:
 
```bash
./gradlew assembleDebug      # genera el APK de depuración
./gradlew installDebug       # instala en un dispositivo/emulador conectado
```
 
### 4. Conceder permisos
 
Al abrir la app por primera vez, se solicitarán progresivamente:
 
1. Acceso a estadísticas de uso (se abre desde los ajustes del sistema).
2. Acceso a notificaciones (se abre desde los ajustes del sistema).
3. Permiso de registro de llamadas y SMS (diálogo en tiempo de ejecución).
Sin estos permisos la app sigue funcionando, pero algunas métricas no estarán disponibles.
 
### 5. Crear una cuenta y empezar a usar la app
 
1. Pulsa **Crear cuenta** e introduce nombre, apellidos, correo y contraseña.
2. La pantalla principal arrancará sin datos: la recopilación empieza a partir de ese momento.
3. `DailyAggregationWorker` se ejecuta cada hora en segundo plano y va completando las métricas. La puntuación de riesgo del día se calcula una vez cerrado dicho día.

## Privacidad

- Solo se capturan metadatos de interacción (marcas de tiempo, duraciones, contadores, nombres de paquete), nunca el contenido de mensajes, llamadas o notificaciones.
- Todo el almacenamiento y procesamiento es local; la app no incluye el permiso `INTERNET`.
- Los datos de uso y métricas se eliminan automáticamente a los 7 días.

