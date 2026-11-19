# Casos de uso y pruebas de aceptación

## Alcance

LessionAngel se conecta por BLE a la placa **ESP32 LESSION**, recibe su señal EMG y permite registrar, analizar, guardar y exportar sesiones. La placa envía una línea de texto por notificación:

```text
t_ms,adc\n
```

`t_ms` es el tiempo desde el arranque de la placa y `adc` es una cuenta de 0 a 4095. El ADC es una envolvente EMG rectificada y suavizada; no es una medición de pulso, fuerza muscular ni milivoltios.

La app tiene tres pantallas: conexión BLE, entrenamiento en tiempo real y análisis de una sesión. El historial persiste en el dispositivo. La exportación crea un CSV para una sesión individual mediante el selector de archivos de Android.

## Casos de uso

| ID | Actor | Caso de uso | Resultado |
|---|---|---|---|
| UC01 | Deportista | Buscar la ESP32 por BLE | La app lista los periféricos BLE detectados. |
| UC02 | Deportista | Seleccionar la placa | Queda seleccionada una placa detectada, no un nombre ficticio. |
| UC03 | Deportista, ESP32 | Conectar por GATT y suscribirse a EMG | La app descubre el servicio UART de la placa y habilita notificaciones TX. |
| UC04 | ESP32 | Enviar una muestra EMG | La app interpreta `t_ms,adc` y valida `adc` en el rango 0–4095. |
| UC05 | Deportista | Iniciar sesión de entrenamiento | Empieza a guardar las muestras recibidas desde ese momento. |
| UC06 | Deportista | Ver la señal en tiempo real | La pantalla muestra el último ADC, tiempo del sensor, contador y gráfico. |
| UC07 | Deportista | Detener la captura | La captura se detiene y las muestras obtenidas quedan disponibles para guardar. |
| UC08 | Deportista | Guardar una sesión y ver su análisis | Se conserva la sesión y se muestran duración, cantidad de muestras y estadísticas ADC. |
| UC09 | Deportista | Consultar sesiones guardadas | Se muestra el historial local, incluso después de reiniciar la app. |
| UC10 | Deportista | Exportar una sesión a CSV | Se guarda un CSV con metadatos de sesión y una fila por muestra. |
| UC11 | Deportista | Iniciar otra sesión | Se inicia una captura nueva sin mezclar muestras con otra sesión. |
| UC12 | Deportista | Borrar la sesión analizada | Se elimina solo la sesión elegida. |
| UC13 | Deportista | Volver a la pantalla anterior | Se navega entre conexión, entrenamiento y análisis. |
| UC14 | Deportista | Salir de la app | La app solicita confirmación antes de cerrarse. |

## Casos de prueba

Las pruebas BLE requieren una ESP32 con el sketch `ESP32_LESSION.ino`, encendida y anunciándose. TC05 y TC07 requieren además un periférico BLE de prueba que permita ofrecer un servicio incorrecto o enviar paquetes arbitrarios. Son pruebas manuales de aceptación; los resultados dependen del teléfono, permisos Android, radio BLE y firmware. Usa sesiones de prueba identificables y no borres los datos preexistentes del usuario.

### TC01 — Solicitar permisos BLE

**Precondición:** permisos BLE aún no concedidos.  
**Pasos:** abrir la app y tocar **Buscar ESP32**; conceder los permisos solicitados.  
**Resultado esperado:** la app inicia el escaneo y muestra su estado. Si se deniegan permisos, informa que son necesarios y no simula que encontró o conectó una placa.

### TC02 — Encontrar la placa anunciada

**Precondición:** ESP32 encendida y anunciando BLE como `ESP32 LESSION`.  
**Pasos:** iniciar una búsqueda y esperar a que finalice.  
**Resultado esperado:** aparece el periférico anunciado; la lista no contiene dispositivos inventados.

### TC03 — No encontrar una placa

**Precondición:** ESP32 apagada o fuera de alcance.  
**Pasos:** iniciar una búsqueda y esperar a que finalice.  
**Resultado esperado:** se informa que no se encontraron placas; no se habilita una conexión ficticia.

### TC04 — Conectar al servicio EMG

**Precondición:** TC02 completada y placa seleccionada.  
**Pasos:** tocar **Conectar**.  
**Resultado esperado:** Android conecta por BLE, descubre el servicio `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` y activa las notificaciones de `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`. La app confirma la suscripción; TC06 comprueba después la recepción de una muestra.

### TC05 — Rechazar un servicio GATT incorrecto

**Precondición:** periférico BLE de prueba que no ofrece el servicio o característica TX de la ESP32.  
**Pasos:** conectarse a ese periférico.  
**Resultado esperado:** la app informa que falta el servicio EMG y no declara que recibe mediciones.

### TC06 — Mostrar una muestra EMG válida

**Precondición:** conexión y notificaciones activas.  
**Pasos:** enviar desde la característica TX una línea válida, por ejemplo `12340,2048` seguida de LF.  
**Resultado esperado:** la lectura muestra `2048 ADC`, el tiempo muestra `12340 ms`, aumenta el contador y se actualiza el gráfico.

### TC07 — Ignorar mensajes incompletos o fuera de rango

**Precondición:** notificaciones activas.  
**Pasos:** enviar una línea incompleta, texto no numérico y una línea con ADC `4096`; luego enviar `12360,2047` seguida de LF.  
**Resultado esperado:** los mensajes inválidos no alteran la lectura; la muestra válida posterior sí se procesa.

### TC08 — No iniciar una sesión sin conexión

**Precondición:** la app está en la pantalla de conexión y no hay conexión GATT activa.  
**Pasos:** observar el botón **Continuar** sin conectar una placa.  
**Resultado esperado:** **Continuar** está deshabilitado; no se puede abrir el entrenamiento ni iniciar una captura sin conexión.

### TC09 — Registrar solo las muestras de la sesión

**Precondición:** BLE conectado y TX notificada.  
**Pasos:** tocar **Iniciar sesión entrenamiento**, enviar al menos tres líneas válidas, y observar el panel.  
**Resultado esperado:** el contador de muestras de la sesión aumenta y cada valor coincide con el ADC enviado por la placa.

### TC10 — Detener y conservar la captura

**Precondición:** sesión activa con muestras recibidas.  
**Pasos:** tocar **Detener**, esperar y observar el contador; después tocar **Guardar y ver análisis**.  
**Resultado esperado:** el contador deja de aumentar mientras la sesión está detenida; las muestras anteriores se guardan y aparecen en el análisis.

### TC11 — No guardar una sesión vacía

**Precondición:** sesión iniciada, sin muestras recibidas.  
**Pasos:** detener la sesión.  
**Resultado esperado:** guardar/análisis no está disponible o informa que no hay muestras; no se agrega una sesión vacía al historial.

### TC12 — Analizar una sesión guardada

**Precondición:** sesión con muestras EMG guardada.  
**Pasos:** abrir el análisis.  
**Resultado esperado:** fecha, duración, cantidad de muestras y estadísticas mínimo, máximo, promedio y última lectura coinciden con las muestras de la sesión; el gráfico usa esos mismos ADC.

### TC13 — Iniciar otra sesión sin mezclar datos

**Precondición:** sesión previa guardada y BLE conectado.  
**Pasos:** tocar **Hacer otra sesión**, iniciar una nueva captura y recibir dos muestras distintas.  
**Resultado esperado:** el contador y el análisis de la nueva sesión contienen solo sus propias muestras; la sesión anterior permanece en el historial.

### TC14 — Conservar el historial tras reiniciar la app

**Precondición:** una o más sesiones guardadas.  
**Pasos:** cerrar y volver a abrir la app; reconectar la ESP32; abrir **Sesiones guardadas**.  
**Resultado esperado:** las sesiones y sus muestras siguen disponibles y se pueden consultar.

### TC15 — Exportar una sesión individual a CSV

**Precondición:** sesión guardada con al menos dos muestras; almacenamiento disponible.  
**Pasos:** abrir el análisis o el historial, tocar **Descargar sesión en CSV** / **Exportar CSV**, elegir ubicación y guardar.  
**Resultado esperado:** se crea un CSV con encabezado `session_id,session_started_at_ms,session_finished_at_ms,sensor_timestamp_ms,emg_adc`, una fila por muestra y los valores originales. Exportar una sesión no incluye lecturas de otras sesiones.

### TC16 — Cancelar la exportación

**Precondición:** sesión guardada.  
**Pasos:** iniciar la exportación y cerrar el selector de documentos sin elegir una ubicación.  
**Resultado esperado:** no se crea ni modifica un archivo y la app permanece en la pantalla actual.

### TC17 — Borrar una sesión

**Precondición:** historial con al menos dos sesiones guardadas.  
**Pasos:** abrir el análisis de una sesión y tocar **Borrar sesión actual**.  
**Resultado esperado:** se elimina solo esa sesión del almacenamiento y del historial; las demás conservan sus datos.

### TC18 — Navegar atrás y cancelar la salida

**Precondición:** app abierta en entrenamiento o análisis.  
**Pasos:** tocar **Atrás** y comprobar la pantalla anterior; después tocar **Salir** y cancelar el diálogo.  
**Resultado esperado:** se vuelve a la pantalla anterior; al cancelar la salida, la app sigue abierta.

### TC19 — Confirmar la salida durante una captura

**Precondición:** sesión activa con al menos una muestra.  
**Pasos:** tocar **Salir** y confirmar. Volver a abrir la app y revisar el historial.  
**Resultado esperado:** la app se cierra y guarda la captura recibida hasta ese momento como sesión consultable.

## Criterio de aceptación

La funcionalidad se acepta cuando TC01–TC19 dan el resultado esperado en un teléfono Android compatible, con el sketch indicado y con el periférico de prueba cuando corresponda. En especial, TC06, TC09, TC10 y TC15 deben confirmar que los datos mostrados, guardados y exportados son las muestras reales recibidas por BLE, no datos simulados.
