# Despliegue de Fiscore — Render + Neon

Guía para publicar la aplicación en **Render** (plan gratuito) con la base de
datos en **Neon** (PostgreSQL serverless, capa gratuita permanente).

El reparto es deliberado: el PostgreSQL gratuito de Render **caduca a los 30
días**, mientras que la capa gratuita de Neon no expira. Render se queda con la
aplicación y Neon con los datos.

---

## 1. Qué hay en el repositorio

| Fichero | Para qué sirve |
|---|---|
| `Dockerfile` | Compila el WAR y produce una imagen ajustada a 512 MB de RAM |
| `render.yaml` | Declara el servicio web y sus variables de entorno |
| `.dockerignore` | Mantiene fuera de la imagen el `target/`, el `.git` y los `.env` |
| `.env.example` | Plantilla de variables; copiar a `.env` para trabajar en local |
| `src/main/resources/application.properties` | Configuración base, toda parametrizada |
| `src/main/resources/application-prod.properties` | Ajustes de producción (caché, cookies seguras, pool para Neon) |

No hay ninguna credencial en el repositorio. Todo lo sensible viaja como
variable de entorno.

---

## 2. Crear la base de datos en Neon

1. Entrar en <https://neon.tech> y crear un proyecto.
2. **Región**: la del proyecto actual es `AWS us-east-2 (Ohio)`, emparejada con
   `region: ohio` en `render.yaml`. Si se cambia una, hay que cambiar la otra:
   cruzar de región añade decenas de milisegundos a cada consulta.
3. Nombrar la base, por ejemplo `fiscore`.
4. Copiar la cadena de conexión que ofrece el panel. Tendrá esta forma:

```
postgresql://usuario:password@ep-xxxx-yyyy.us-east-1.aws.neon.tech/fiscore?sslmode=require&channel_binding=require
```

No sirve tal cual: hay que traducirla a JDBC.

---

## 3. Traducir la cadena de Neon a JDBC

Neon entrega el formato de `libpq`; el driver de Java necesita otro. De la
cadena anterior salen tres valores:

```
DB_URL      = jdbc:postgresql://ep-xxxx-yyyy.us-east-1.aws.neon.tech/fiscore?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory
DB_USERNAME = usuario
DB_PASSWORD = password
```

Cuatro detalles que suelen costar una tarde:

- **El usuario y la contraseña salen de la URL.** En JDBC van aparte, no
  incrustados en la cadena.
- **`sslmode=verify-full`, no `require`.** `require` cifra pero **no valida el
  certificado**, lo que deja la conexión expuesta a un intermediario.
  `verify-full` con `sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` valida
  contra el almacén de certificados de la JVM, que ya confía en la CA de Neon.
- **Quitar el sufijo `-pooler` del host.** Se usa el endpoint directo: HikariCP
  ya agrupa las conexiones, y el pooler de Neon rompe los *prepared statements*
  de Hibernate salvo que se añada `&prepareThreshold=0`.
- **No copiar `channel_binding`.** Es un parámetro de `libpq`; el driver JDBC lo
  ignora y solo ensucia la cadena.

---

## 4. Publicar en Render

1. Subir el repositorio a GitHub.
2. En <https://dashboard.render.com> → **New** → **Blueprint**, y apuntar al
   repositorio. Render lee `render.yaml` y propone el servicio.
3. Render pedirá las variables marcadas como `sync: false`, que son las que
   nunca deben quedar escritas en el repositorio:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | La cadena JDBC del paso 3 |
   | `DB_USERNAME` | Usuario de Neon |
   | `DB_PASSWORD` | Contraseña de Neon |
   | `SEED_ADMIN_MAIL` | Correo del administrador inicial |

4. `SEED_ADMIN_PASS` la genera Render automáticamente (`generateValue: true`).
   Se consulta en **Environment** una vez creado el servicio: es la contraseña
   con la que se entra la primera vez.
5. Desplegar. El primer arranque tarda algunos minutos porque compila el WAR
   dentro de la imagen.

> **El esquema ya está creado.** Se generó y verificó contra el proyecto de Neon
> antes del primer despliegue: existen las doce tablas, los parámetros de
> `DTE_PARAMETRO` y los correlativos de `DTE_CORRELATIVO`. Lo único que falta es
> el usuario administrador, que se crea en el primer arranque en Render con la
> contraseña que genera `SEED_ADMIN_PASS`.

### Qué ocurre en el primer arranque

Con `DDL_AUTO=update`, Hibernate crea todas las tablas en la base vacía de Neon.
Acto seguido la aplicación siembra por su cuenta:

- el **usuario administrador** (solo si `ADM_USUARIOS` está vacía);
- los **parámetros de emisión DTE** en `DTE_PARAMETRO`, con sus valores por
  defecto;
- los **correlativos** de cada tipo de documento en `DTE_CORRELATIVO`.

---

## 5. Primeros pasos tras el despliegue

1. Entrar con `admin` y la contraseña que generó Render.
2. **Cambiar esa contraseña.**
3. Ir a **Configuración → Parámetros DTE** y rellenar los datos reales del
   emisor: NIT, NRC, código de actividad, dirección y códigos de departamento y
   municipio.
4. Mientras `MH_AMBIENTE` valga `00`, el sistema trabaja en **ambiente de
   pruebas** y los documentos que emita **no tienen validez fiscal**; la hoja
   imprimible lo advierte. Pasar a `01` solo con las credenciales de producción
   del Ministerio de Hacienda ya cargadas.
5. Opcional: poner `SEED_ADMIN=false` para que los arranques posteriores ni
   siquiera consulten la tabla de usuarios.

---

## 6. Sobre el esquema: `update` frente a migraciones versionadas

Aquí hay una **diferencia deliberada con el proyecto FoodStop**, que usa Flyway
con `ddl-auto=validate`.

Fiscore está construido hoy sobre `ddl-auto=update`: es lo que ha venido creando
las tablas y añadiendo columnas (`USER_ROL`, los índices únicos de `factura`, la
tabla `DTE_CORRELATIVO`). Escribir a mano una migración base para las doce
tablas antes del primer despliegue añadía un riesgo real —si un solo tipo o
restricción no coincide, `validate` impide que la aplicación arranque— sin
resolver ningún problema inmediato.

`update` tiene un límite conocido: **crea y amplía, pero nunca borra ni
modifica**. Si se renombra una columna o se cambia un tipo, el cambio no se
aplica y el esquema se va desviando en silencio.

Cuando la aplicación tenga datos que no se puedan perder, conviene pasar a
migraciones versionadas:

1. Generar el esquema actual:
   ```
   ./mvnw spring-boot:run \
     -Dspring-boot.run.jvmArguments="-Dspring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create -Dspring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=esquema.sql"
   ```
2. Guardarlo como `src/main/resources/db/migration/V1__esquema_inicial.sql`.
3. Añadir la dependencia `flyway-core` (y `flyway-database-postgresql`).
4. Poner `spring.flyway.baseline-on-migrate=true` para que adopte la base ya
   existente sin volver a crearla.
5. Cambiar la variable `DDL_AUTO` a `validate` en Render.

Al estar el valor en una variable de entorno, el cambio no exige tocar código.

---

## 7. Peculiaridades de los planes gratuitos

**Render suspende el servicio tras 15 minutos sin tráfico.** La siguiente visita
espera entre 40 y 60 segundos mientras el contenedor arranca. No es un fallo. Si
molesta, un plan de pago lo elimina; un *pinger* externo cada 10 minutos también,
aunque consume las horas gratuitas.

**Neon suspende el compute tras unos 5 minutos de inactividad.** Por eso el
perfil de producción deja que el pool se vacíe (`minimum-idle=0`): mantener
conexiones abiertas impediría esa suspensión y agotaría las horas gratuitas. El
precio son 1-3 segundos de espera en la primera consulta después de un rato
parado.

Los dos arranques en frío se suman: la primera visita tras una noche sin uso
puede tardar cerca de un minuto.

**512 MB de RAM.** Las opciones de JVM del `Dockerfile` están calculadas para
ese techo (heap al 55 %, recolector serie, tope de metaspace). Conviene no
tocarlas sin medir.

### Medidas reales

La imagen se probó en local con las mismas restricciones que impone Render
(`docker run -m 512m --cpus 0.5`):

| Medida | Valor |
|---|---|
| Tamaño de la imagen | 572 MB |
| Memoria en reposo con sesión abierta | 287 MB de 512 MB (56 %) |
| Arranque con 0.5 CPU | ~140 s |
| Arranque sin límite de CPU | ~17 s |

El arranque depende mucho de la CPU disponible, y el plan gratuito de Render
garantiza 0.1 CPU (con ráfagas por encima). Conviene contar con que el primer
despliegue y cada salida de suspensión tarden **más de dos minutos**, no los
40-60 s que suele citarse para aplicaciones más ligeras. Si Render marca el
servicio como no disponible por el `healthCheckPath`, hay que darle margen: el
health check no debe interpretarse como fallo durante ese arranque.

---

## 8. Trabajar en local

```bash
cp .env.example .env      # y rellenar los valores
JAVA_HOME=/c/Users/gustavo.cruz/.jdks/corretto-17.0.17 ./mvnw spring-boot:run
```

Sin perfil activo se usa la configuración base: PostgreSQL local, contexto
`/fiscore` y caché de plantillas desactivada.

Para probar la imagen tal como correrá en Render:

```bash
docker build -t fiscore .
docker run --rm -p 8080:8080 \
  -e CONTEXT_PATH=/ \
  -e DB_URL="jdbc:postgresql://host.docker.internal:5432/contasuite" \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=root \
  -e SEED_ADMIN_PASS=UnaClaveSegura \
  fiscore
```

---

## 9. Copiar datos de la base local a Neon

No hace falta tener `psql` instalado: sirve una imagen de PostgreSQL en Docker.

**Cuidado con el alcance.** La base local `contasuite` comparte espacio con las
tablas de otro proyecto (gestión de riesgos: `ambito_riesgo`, `control`,
`evaluacion_riesgo`…). Hay que copiar **solo** las de Fiscore, nunca la base
entera.

```bash
LOCAL="-h host.docker.internal -U postgres -d contasuite"
NEON="postgresql://neondb_owner@<host-directo>/neondb?sslmode=require"

# 1. Volcar tabla por tabla EN ORDEN DE DEPENDENCIAS. Un volcado único no sirve:
#    pg_dump ordena alfabéticamente y las claves foráneas fallarían
#    (detalle_factura iría antes que factura, contrato_servicio antes que servicio).
for t in adm_usuarios cliente servicio dte_parametro contrato \
         contrato_servicio proyecto factura detalle_factura; do
  docker run --rm -e PGPASSWORD=<clave-local> postgres:17-alpine \
    pg_dump $LOCAL --data-only --column-inserts -t public.$t \
    | grep '^INSERT INTO' >> datos.sql
done

# 2. Cargar con --single-transaction, precedido de los DELETE en orden inverso.
cat migracion.sql | docker run -i --rm -e PGPASSWORD=<clave-neon> postgres:17-alpine \
  psql "$NEON" -v ON_ERROR_STOP=1 --single-transaction -f -
```

Dos cosas que hay que hacer **después de cargar**, o el primer alta falla:

```sql
-- Los contadores de identidad quedan en 1 tras insertar ids explícitos:
--   sin esto, el siguiente registro choca con uno existente.
SELECT setval(pg_get_serial_sequence('cliente','id'),
              COALESCE((SELECT MAX(id) FROM cliente),0)+1, false);
-- ... y lo mismo para servicio, contrato, contrato_servicio, proyecto,
--     factura, detalle_factura y dte_parametro.

-- Los correlativos de DTE conviene derivarlos de las facturas cargadas en vez
-- de copiarlos: así quedan coherentes aunque el origen estuviera desfasado, y
-- se conservan las filas de los cinco tipos de documento.
UPDATE dte_correlativo c
   SET dtco_ultimo = COALESCE((
         SELECT MAX(CAST(regexp_replace(f.numero_factura,'\D','','g') AS bigint))
           FROM factura f
          WHERE f.tipo_dte = c.dtco_tipo_dte
            AND f.numero_factura ~ '[0-9]'), 0);
```

Si el correlativo se queda corto, la siguiente emisión repite número y la
rechaza el índice único `uk_factura_numero_control`.

---

## 10. Si algo falla

| Síntoma | Causa habitual |
|---|---|
| `FATAL: password authentication failed for user "x"` (comillas **dobles**) | Lo dice PostgreSQL. El usuario y la contraseña siguen dentro de `DB_URL`; deben ir en `DB_USERNAME` y `DB_PASSWORD` |
| `ERROR: password authentication failed for user 'x'` (comillas **simples**, `SQLState 28P01`) | Lo dice el proxy de Neon, no PostgreSQL. La conexión llegó bien —host, DNS y TLS correctos—, así que solo falla la credencial: `DB_PASSWORD` regenerada en Neon, pegada en Render con un espacio o un salto de línea de más, o copiada todavía percent-encoded de la cadena de `libpq` (`%2F` es `/`, `%2B` es `+`). Neon devuelve este mismo mensaje genérico cuando el rol no existe en el endpoint indicado, así que conviene comprobar también que el host de `DB_URL` sigue siendo el de la rama actual |
| `The server does not support SSL` | Falta `sslmode` en la cadena, o se usó `postgresql://` en lugar de `jdbc:postgresql://` |
| `PKIX path building failed` | `verify-full` sin `sslfactory=...DefaultJavaSSLFactory` |
| `prepared statement "S_1" already exists` | Se está usando el endpoint `-pooler`; cambiar al directo o añadir `&prepareThreshold=0` |
| La primera petición tarda un minuto | Arranque en frío de Render sumado al despertar de Neon (ver §7) |
| El contenedor muere sin traza | Falta de memoria: revisar `JAVA_OPTS` en el `Dockerfile` |
| `NullPointerException: entry` en `JarWarResourceSet` | Hay algún `.jar` con espacios en el nombre dentro de `src/main/webapp/WEB-INF/lib`; el `pom.xml` ya excluye ese directorio del empaquetado |
| `ContextPath must start with '/' and not end with '/'` | Se definió `CONTEXT_PATH=/`; para la raíz debe quedar **vacío** (el perfil `prod` ya lo hace) |
| No se puede entrar con `admin` | La tabla `ADM_USUARIOS` ya tenía usuarios, así que `SEED_ADMIN_PASS` no se aplicó |
| Los POST devuelven 403 | La cookie `XSRF-TOKEN` no llega; comprobar que `CONTEXT_PATH` coincide con la ruta real |

### Comprobar la credencial de Neon sin usar el puerto 5432

Ante un `28P01` lo primero es saber si la contraseña es mala o si lo que está
mal es el valor guardado en Render. Cada intento de redespliegue cuesta varios
minutos de compilación, así que conviene resolver la duda fuera de Render.

El camino obvio —`psql` o una prueba JDBC— **no siempre está disponible**: en una
red corporativa el puerto 5432 saliente suele estar bloqueado, y entonces la
prueba falla con `SQLState 08001` / `Connect timed out` sin llegar a decir nada
sobre la contraseña. Ese timeout es del firewall local; no significa que la
credencial sea incorrecta ni que Neon esté caído.

Neon expone además un endpoint **SQL sobre HTTPS** (el que usa su driver
*serverless*), que va por el 443 y por tanto atraviesa esos firewalls:

```bash
curl -s -X POST "https://<host>/sql" \
  -H "Neon-Connection-String: postgresql://<usuario>:<clave>@<host>/<base>?sslmode=require" \
  -H "Neon-Raw-Text-Output: true" \
  -H "Content-Type: application/json" \
  -d '{"query":"select current_user, current_database()","params":[]}'
```

Responde con las filas en JSON si la credencial es buena, y con un error de
autenticación si no lo es. Sirve con el host directo y con el `-pooler`, lo que
de paso confirma que ambos endpoints existen antes de fijar `DB_URL`.

Dos advertencias:

- **No recorre el mismo camino que la aplicación.** Valida la credencial, no la
  ruta TCP al 5432 desde Render. Es suficiente cuando el error de Render fue un
  `28P01`, porque ese código ya prueba que la petición llegó al proxy de Neon y
  que lo único en discusión era la contraseña. Ante un error de red en Render,
  esta comprobación no dice nada.
- **La clave viaja en la línea de órdenes**, y queda en el historial del intérprete
  y en la lista de procesos. Conviene pasarla por variable de entorno y borrar la
  entrada del historial después.
