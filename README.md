# CoreBank — Java / Spring Boot

Banco digital simulado (login, depósitos, extracciones, transferencias
internas y externas simuladas, historial, notificaciones en tiempo real)
construido de punta a punta: backend en Java/Spring Boot, frontend en
React, Postgres como base de datos, y desplegado en un VPS propio detrás
de nginx con HTTPS real.

**PROBALA ACA:** [COREBANK.COM](https://169-58-140-99.sslip.io)

**Usuarios de prueba** (clickeables en la pantalla de login, autocompletan):

| Usuario = `JGONZALE` | Contraseña = `demo1234` |

| Usuario = `MRODRIGU` | Contraseña = `demo1234` |

| Usuario = `LFERNAND` | Contraseña = `demo1234` |

| Usuario = `SPEREYRA` | Contraseña = `demo1234` |

| Usuario = `CSUAREZ`  | Contraseña = `demo1234` |



## Stack

**Backend**
- Java 17 + Spring Boot 3.3 (`spring-boot-starter-web`, `-websocket`, `-jdbc`)
- `JdbcTemplate`
- Autenticación con JWT (`jjwt`) + hash de contraseñas con BCrypt
  (`spring-security-crypto`)
- `Filter` propios (no Spring Security) para CORS, rate limiting y
  autenticación, encadenados con `Ordered`
- WebSockets nativos de Spring para notificaciones push
- PostgreSQL

**Frontend**
- React 19 + Vite
- `axios` para HTTP, `socket.io-client`-equivalente nativo (`WebSocket`
  API) para el push de notificaciones
- Sin librería de estado externa — el estado vive en el propio componente

**Infraestructura**
- Docker Compose (4 servicios de la app + nginx + certbot)
- nginx como reverse proxy y servidor de estáticos
- Let's Encrypt / Certbot para HTTPS, con renovación automática

Ver [DEPLOYMENT.md](DEPLOYMENT.md) para el detalle completo del
despliegue (arquitectura del reverse proxy, HTTPS, runbook paso a paso).

## Arquitectura de la aplicación

```
React (Vite) ──HTTP/JSON──► Spring Boot ──JDBC──► PostgreSQL
      ▲                          │
      └──────WebSocket───────────┘
         (notificaciones push)
```

- El frontend habla con el backend por HTTP (`/api/*`) y mantiene una
  conexión WebSocket (`/ws`) abierta para recibir notificaciones en
  tiempo real (por ejemplo, cuando otro usuario te transfiere plata).
- El backend no usa un ORM: cada operación de negocio (`CajaService`,
  `TransferenciaService`, `HistorialService`) arma su propio SQL con
  `JdbcTemplate` y lo corre dentro de una transacción explícita
  (`TransactionTemplate`). Es más código que anotar entidades con JPA,
  pero deja el control total sobre qué se bloquea, en qué orden, y qué
  transacción exacta se corre — importante en un sistema donde una
  transferencia mueve dinero de dos cuentas a la vez.
- Dos integraciones externas están **simuladas a propósito**:
  `CoelsaService` (cámara compensadora interbancaria) y `ArcaService`
  (autoridad fiscal, emisión de CAE) son clases que devuelven una
  respuesta con latencia y fallos aleatorios, imitando cómo se comporta
  una API externa real sin depender de una de verdad. Están claramente
  marcadas como simulación en el código — la lógica que las rodea
  (manejo de errores, reintentos, rechazo de la operación) es real.

### Endpoints principales

| Método | Ruta | Qué hace |

| `POST` | `/api/login` | Autentica, devuelve JWT |

| `POST` | `/api/transaccion` | Depósito o extracción |

| `POST` | `/api/transferencia` | Transferencia interna o externa |

| `POST` | `/api/historial` | Movimientos de una cuenta |

|   —   | `/ws` | WebSocket, notificaciones push |

## Decisiones de diseño que vale la pena mirar

**Bloqueo de filas en orden alfabético, no en orden de llegada.**
`TransferenciaService.registrarTransferencia` (ver
[`TransferenciaService.java`](backend/src/main/java/com/corebank/domain/TransferenciaService.java))
siempre bloquea las dos cuentas (`SELECT ... FOR UPDATE`) ordenadas por
`id_usuario`, sin importar quién es el origen y quién el destino. Si dos
transferencias simultáneas van en direcciones opuestas entre las mismas
dos cuentas (A→B y B→A al mismo tiempo) y cada una bloqueara primero su
propio origen, es la receta clásica para un deadlock. Bloqueando siempre
en el mismo orden global, las dos transacciones compiten por la primera
fila en vez de bloquearse mutuamente.

**Idempotencia vía constraint de base de datos, no vía lógica de
aplicación.** Cada transferencia lleva un `id_idempotencia` con un
`UNIQUE` en la tabla `operacion`. Si el cliente reintenta el mismo
pedido (por ejemplo, por un timeout de red donde no sabe si la primera
transferencia se aplicó o no), el `INSERT` duplicado tira una
`DuplicateKeyException` que el servicio traduce a un rechazo explícito
en vez de cobrar dos veces. Es la base de datos, no un `if`, la que
garantiza que no hay dos transferencias con el mismo intento.

**Rate limiting con token bucket propio, no una librería.** La primera
versión usaba `RateLimiter` de Guava, que arranca en cero tokens y los
va acumulando con el reloj — con una tasa baja (5 intentos de login por
minuto), un cliente nuevo podía ser rechazado en su primerísimo pedido.
[`TokenBucket.java`](backend/src/main/java/com/corebank/web/TokenBucket.java)
es una implementación de ~30 líneas que arranca con la ráfaga completa
disponible, que es el comportamiento esperado.

**Filtros con orden explícito.** CORS, rate limiting y autenticación son
`Filter`s independientes, y el orden en que corren importa: si CORS no
corre primero, una respuesta 401 o 429 generada por los otros dos sale
sin los headers `Access-Control-Allow-*`, y el navegador la descarta por
completo aunque el status code sea correcto. `CorsConfig` se registra
explícitamente con `Ordered.HIGHEST_PRECEDENCE` para garantizar esto.

**Montos como enteros, nunca como `float`/`double`.** Todo monto viaja y
se persiste en centavos (`long`), y sólo se convierte a string decimal en
el borde con el cliente. Evita el clásico problema de redondeo de punto
flotante en dinero.

## Estructura del repo

```
PROYECTO PAL VPS JAVA/
  README.md                 — este archivo
  docker-compose.yml
  .env                       — DOMAIN=... (no es secreto)
  db/
    schema.sql               — DDL + usuarios de demo
  backend/
    Dockerfile
    pom.xml
    src/main/java/com/corebank/
      CorebankApplication.java
      auth/                  — JWT y el filtro de autenticación
      config/                — CORS, WebSocket, datasource
      domain/                — lógica de negocio (caja, transferencias, historial)
      external/               — integraciones simuladas (COELSA, ARCA)
      web/                    — controllers, DTOs, rate limiting
      ws/                      — WebSocket hub y handshake
  frontend/
    Dockerfile
    src/
      App.jsx                — toda la UI (login, dashboard, operaciones)
      App.css
  nginx/
    templates/                — config activa (arranca en HTTP)
    https-ready/               — config final con TLS
```

