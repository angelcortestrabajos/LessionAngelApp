# Documentación obligatoria

**Autor:** Ángel Cortés  
**Documento original:** [Google Docs](https://docs.google.com/document/d/1aDvTDrZWFIhGRC6rpoIOfyW68UIJ0yxsOR-QBw6E2ZE/edit)

Este archivo conserva las notas de relevamiento, historias de usuario, tareas y bitácora del documento compartido. Las ideas iniciales que luego se cambiaron (por ejemplo, Wi‑Fi y métricas que la placa no mide) se mantienen aquí como antecedentes; el alcance vigente de la aplicación es el descrito en el [README](../README.md) y en los [casos de uso y pruebas actuales](casos-de-uso-y-pruebas.md).

## Toma de notas — 03/11/26

### Usuario Luis

**No quiere:**

- Datos simulados.
- Inicio de sesión o perfil.
- Que importe en qué músculo se conecte la banda.
- Funciones relacionadas con deportes.

**Sí quiere:**

- Una aplicación sencilla, de tres pantallas como máximo, que funcione y se conecte con el microcontrolador ESP32 para mostrar datos reales.

### Usuario Ignacio

**No quiere:**

- Datos simulados.
- Inicio de sesión o perfil.

**Sí quiere:**

- Los colores de Lession.
- Una aplicación sencilla.

## Preguntas y respuestas

1. **¿Es necesario usar Taiga para crear las historias de usuario con sus tareas?** No.
2. **¿Es necesario almacenar en una base de datos los datos de cada sesión?** Sí.
3. **¿Es correcta la estructura propuesta para la aplicación?** Sí.
4. **¿Es necesario guardar continuamente para prevenir la pérdida de datos si la app se cierra accidentalmente?** No.

## Estructura inicial propuesta

1. **Conexión:** logo Lession, selección de Bluetooth o Wi‑Fi, lista de dispositivos o redes, botón para conectar y estado de conexión.
2. **Toma de datos:** botones para comenzar y detener; métricas y gráficos en tiempo real; opción para guardar y mostrar los datos.
3. **Resultados:** resultados y análisis detallado; opciones para salir con confirmación, iniciar otra sesión o borrar la sesión.

> **Cambio de alcance:** la propuesta de conexión por Wi‑Fi y las métricas genéricas se registran como requisitos iniciales. La implementación actual usa BLE y muestra únicamente la envolvente EMG que transmite la ESP32; no inventa frecuencia cardíaca, esfuerzo, fatiga ni pasos.

## Historias de usuario originales

| ID | Historia de usuario | Prioridad |
|---|---|---|
| HU01 | Como deportista, quiero conectar mi banda mediante Bluetooth o Wi‑Fi para transmitir los datos de sus sensores a la aplicación. | Alta |
| HU02 | Como deportista, quiero iniciar y detener la toma de datos para controlar mi entrenamiento en tiempo real. | Alta |
| HU03 | Como deportista, quiero visualizar mis métricas mediante gráficos en tiempo real para conocer mi rendimiento físico. | Alta |
| HU04 | Como deportista, quiero guardar y consultar los resultados de mi entrenamiento para analizar mi rendimiento. | Media |
| HU05 | Como deportista, quiero iniciar una sesión nueva, borrar una sesión anterior o salir de la aplicación para administrar mis entrenamientos. | Baja |

## Tareas originales

### HU01 — Conexión de dispositivos

1. Diseñar la pantalla inicial con el logo de Lession.
2. Crear los botones de conexión Bluetooth y Wi‑Fi.
3. Implementar la búsqueda de dispositivos disponibles.
4. Mostrar los dispositivos encontrados en un menú desplegable.
5. Implementar la conexión con el dispositivo seleccionado.
6. Mostrar el estado de conexión.

### HU02 — Toma de datos

1. Diseñar la pantalla de medición.
2. Crear el botón **Comenzar con la toma de datos**.
3. Crear el botón **Detener**.
4. Implementar la recepción de datos de los sensores.
5. Controlar el inicio y la finalización de las mediciones.

### HU03 — Visualización de métricas

1. Diseñar el espacio para mostrar las métricas.
2. Implementar gráficos en tiempo real.
3. Mostrar frecuencia cardíaca.
4. Mostrar nivel de esfuerzo muscular.
5. Mostrar fatiga muscular.
6. Actualizar los gráficos automáticamente.

> **Cambio de alcance:** las tareas 3–5 corresponden a la idea original. La placa utilizada no transmite esas mediciones; la app actual muestra el valor ADC de la señal EMG.

### HU04 — Guardado y análisis de resultados

1. Crear el botón **Guardar y mostrar datos**.
2. Diseñar la pantalla de resultados.
3. Mostrar los valores registrados durante el entrenamiento.
4. Generar gráficos con los datos obtenidos.
5. Implementar un análisis detallado.
6. Guardar resultados para futuras consultas.

### HU05 — Administración de sesiones

1. Crear el botón **Salir**.
2. Implementar el mensaje de confirmación de salida.
3. Agregar opciones para confirmar o cancelar la salida.
4. Crear el botón **Hacer una nueva sesión de entrenamiento**.
5. Crear el botón **Borrar sesión**.
6. Limpiar los datos de la sesión seleccionada.

## Bitácora de trabajo

### 04/11

- Creación del repositorio de GitHub e invitación a los profesores como colaboradores.
- Primer commit con el `README.md`.
- Finalización de las preguntas a los stakeholders sobre los detalles de la aplicación.

### 05/11

- Inicio del desarrollo de la aplicación.
- Prompt inicial para Android Studio: implementar las tres pantallas, con conexión, captura, gráficos, resultados, análisis y administración de sesiones.
- Luego de pegar el código en Android Studio, se corrigieron errores con Copilot y se ejecutó la aplicación.
- Se agregó el logo de Lession, se aplicaron sus colores y se ajustó el contraste de textos y gráficos.

### 09/11

- Integración de Firebase con la aplicación.
- Se configuró el inicio de sesión anónimo para sincronizar sesiones sin pedir al usuario un perfil o inicio de sesión explícito.

### 10/11 y 11/11

- Se proporcionó al agente el código de la ESP32 para reemplazar las mediciones simuladas por las mediciones que realmente entrega el prototipo.
- Se solicitó eliminar las mediciones que la placa no transmite y mostrar los datos reales del sensor.

### 12/11

- Se probó la conexión con la ESP32.
- Se eliminó Wi‑Fi de la aplicación y se implementó la búsqueda y conexión BLE real.
- Se solicitó buscar la placa anunciada como **ESP32 LESSION** y permitir continuar solo después de conectar realmente.
- En las pruebas de la bitácora se reportó que la app se conectaba correctamente a la ESP32 y recibía datos en tiempo real.

### 16/11 y 17/11

- Se agregó la exportación individual de sesiones de entrenamiento en archivos CSV.
- Se realizaron pruebas de la aplicación.

### 18/11

- Finalización de la aplicación.

### 19/11

- Correcciones de acuerdo con las observaciones del profesor Ignacio.
