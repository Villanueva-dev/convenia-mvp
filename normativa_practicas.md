# Contexto Normativo: Resolución CF No. 002 de 2024 (Reglamento de Prácticas FI)

## 1. Requisitos de Elegibilidad del Estudiante
Para que el sistema permita iniciar una solicitud de práctica, el estudiante debe cumplir con:
* **Créditos Aprobados:** Mínimo el 80% de los créditos académicos para programas Profesionales, y el 70% para programas Tecnológicos.
* **Seminarios:** Haber cursado y aprobado dos (2) seminarios obligatorios de preparación para la práctica.

## 2. Modalidades y Tipos de Vinculación
La base de datos (Entidad `Agreement`) debe restringir las opciones a los siguientes valores (Enums):
* **Modalidad de la Práctica:** PROFESIONAL, SOCIAL, INVESTIGATIVA, INTERNACIONAL.
* **Tipo de Vinculación:** CONTRATO_LABORAL, CONTRATO_APRENDIZAJE, CONVENIO_PASANTIA, CONVENIO_MARCO.

## 3. Reglas de Duración y Tiempo
El sistema debe validar las fechas de inicio y fin del convenio bajo estas reglas:
* **Duración Estándar:** Mínimo de cuatro (4) meses y un máximo de un (1) año.
* **Intensidad Horaria:** Mínimo de 20 horas semanales y un máximo de 48 horas semanales.
* **Excepción estricta:** Si el tipo de vinculación es CONTRATO_APRENDIZAJE (Ley 789 de 2002), la duración debe ser obligatoriamente de seis (6) meses.

## 4. Gestión Documental Obligatoria
El sistema debe requerir (o prever la carga de) los siguientes documentos para legalizar la práctica:
* Hoja de Vida.
* Copia del contrato laboral o de aprendizaje.
* Copia de la Cédula de Ciudadanía.
* Certificados de afiliación a EPS y ARL.
* Plan de Trabajo concertado.

## 5. Actores y Roles del Sistema
El control de acceso (RBAC) debe contemplar a los siguientes actores:
* **Estudiante:** Ejecutor de la práctica.
* **Coordinación de Prácticas:** Autoriza el inicio, coordina el proceso y administra el sistema de información.
* **Docente Asesor (Universidad):** Acompaña al estudiante, registra visitas y emite el 50% de la nota.
* **Tutor/Co-formador (Empresa):** Guía al estudiante en la empresa y emite el otro 50% de la nota.

## 6. Lógica de Seguimiento y Evaluación (Crítico)
* **Visitas Mínimas:** El sistema no debe permitir cerrar el ciclo de la práctica si el `Docente Asesor` no ha registrado al menos tres (3) visitas o seguimientos.
* **Cálculo de Nota Final:** La plataforma debe calcular la nota final promediando: 50% de la nota del Docente Asesor + 50% de la nota del Tutor de la Empresa. Ambas evaluaciones deben ser requeridas para emitir el certificado final.