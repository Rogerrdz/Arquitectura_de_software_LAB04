## Laboratorio #4 – REST API Blueprints (Java 21 / Spring Boot 3.3.x)
# Escuela Colombiana de Ingeniería – Arquitecturas de Software  

---

## 📋 Requisitos
- Java 21
- Maven 3.9+

## ▶️ Ejecución del proyecto
```bash
mvn clean install
mvn spring-boot:run
```
Probar con `curl`:
```bash
curl -s http://localhost:8080/blueprints | jq
curl -s http://localhost:8080/blueprints/john | jq
curl -s http://localhost:8080/blueprints/john/house | jq
curl -i -X POST http://localhost:8080/blueprints -H 'Content-Type: application/json' -d '{ "author":"john","name":"kitchen","points":[{"x":1,"y":1},{"x":2,"y":2}] }'
curl -i -X PUT  http://localhost:8080/blueprints/john/kitchen/points -H 'Content-Type: application/json' -d '{ "x":3,"y":3 }'
```

> Si deseas activar filtros de puntos (reducción de redundancia, *undersampling*, etc.), implementa nuevas clases que implementen `BlueprintsFilter` y cámbialas por `IdentityFilter` con `@Primary` o usando configuración de Spring.
---

Abrir en navegador:  
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)  

---

## 🗂️ Estructura de carpetas (arquitectura)

```
src/main/java/edu/eci/arsw/blueprints
  ├── model/         # Entidades de dominio: Blueprint, Point
  ├── persistence/   # Interfaz + repositorios (InMemory, Postgres)
  │    └── impl/     # Implementaciones concretas
  ├── services/      # Lógica de negocio y orquestación
  ├── filters/       # Filtros de procesamiento (Identity, Redundancy, Undersampling)
  ├── controllers/   # REST Controllers (BlueprintsAPIController)
  └── config/        # Configuración (Swagger/OpenAPI, etc.)
```

> Esta separación sigue el patrón **capas lógicas** (modelo, persistencia, servicios, controladores), facilitando la extensión hacia nuevas tecnologías o fuentes de datos.

---

## 📖 Actividades del laboratorio

### 1. Familiarización con el código base
- Revisa el paquete `model` con las clases `Blueprint` y `Point`.  
- Entiende la capa `persistence` con `InMemoryBlueprintPersistence`.  
- Analiza la capa `services` (`BlueprintsServices`) y el controlador `BlueprintsAPIController`.

### 2. Migración a persistencia en PostgreSQL
- Configura una base de datos PostgreSQL (puedes usar Docker).  

  Con la ayuda de docker creamos un archivo llamado 'docker-compose.yml' para levantar la base de datos , sin necesidad de instalar PostgreSQL :

  ```yml 
    version: '3.8'
    services:
      postgres:
        image: postgres:15
        container_name: blueprints-db
        restart: always
        environment:
          POSTGRES_DB: blueprintsdb
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        ports:
          - "5432:5432"
        volumes:
          - pgdata:/var/lib/postgresql/data

    volumes:
      pgdata:

  ```

  Luego ejecutamos:

  ```bash
    docker compose up -d
  ```
  Con esto leemos el archivo y creaamos:

  - Base de datos: blueprintsdb
  - Usuario: postgres
  - Password: postgres
  - Puerto: 5432


  Agregamos esto tambien en las application.properties:

```bash
    spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER
    server.port=8081

    spring.datasource.url=jdbc:postgresql://localhost:5432/blueprintsdb
    spring.datasource.username=Postgres
    spring.datasource.password=Postgres
    spring.jpa.hibernate.ddl-auto=update
    spring.jpa.show-sql=true
``` 
  Creamos la estructura de la base de datos conectandonos desde la terminal  con :
  
  ```bash
    docker exec -it blueprints-db psql -U postgres -d blueprintsdb
  ```
  Una vez dentro creamols las tablas :

  ```sql
      CREATE TABLE blueprint (
      id SERIAL PRIMARY KEY,
      author VARCHAR(100) NOT NULL,
      name VARCHAR(100) NOT NULL
  );

      CREATE TABLE point (
      id SERIAL PRIMARY KEY,
      blueprint_id INTEGER REFERENCES blueprint(id) ON DELETE CASCADE,
      x INTEGER NOT NULL,
      y INTEGER NOT NULL
  );

  ```


- Implementa un nuevo repositorio `PostgresBlueprintPersistence` que reemplace la versión en memoria.  

Agregamos la dependencia al Pom : 

```xml
    <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    </dependency>

```
Creamos la clase 'PostgresBlueprintPersistence' que implementa 'BlueprintPersistence' :

```java
@Repository
public class PostgresBlueprintPersistence implements BlueprintPersistence {

    private final String url = "jdbc:postgresql://localhost:5432/blueprintsdb";
    private final String user = "postgres";
    private final String password = "postgres";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

```

Implementamos las funciones :

-  'saveBlueprint()' :

```java
@Override
public void saveBlueprint(Blueprint bp)
        throws BlueprintPersistenceException {

    try (Connection conn = getConnection()) {

        // Insert blueprint
        String insertBlueprint =
            "INSERT INTO blueprint(author, name) VALUES (?, ?) RETURNING id";

        PreparedStatement ps =
            conn.prepareStatement(insertBlueprint);

        ps.setString(1, bp.getAuthor());
        ps.setString(2, bp.getName());

        ResultSet rs = ps.executeQuery();
        rs.next();
        int blueprintId = rs.getInt("id");

        // Insert points
        String insertPoint =
            "INSERT INTO point(blueprint_id, x, y) VALUES (?, ?, ?)";

        PreparedStatement psPoint =
            conn.prepareStatement(insertPoint);

        for (Point p : bp.getPoints()) {
            psPoint.setInt(1, blueprintId);
            psPoint.setInt(2, p.getX());
            psPoint.setInt(3, p.getY());
            psPoint.addBatch();
        }

        psPoint.executeBatch();

    } catch (SQLException e) {
        throw new BlueprintPersistenceException(
            "Error saving blueprint", e);
    }
}

```

- 'getBlueprint()':

```java
@Override
public Blueprint getBlueprint(String author, String name)
        throws BlueprintPersistenceException {

    try (Connection conn = getConnection()) {

        String queryBlueprint =
            "SELECT id FROM blueprint WHERE author=? AND name=?";

        PreparedStatement ps =
            conn.prepareStatement(queryBlueprint);

        ps.setString(1, author);
        ps.setString(2, name);

        ResultSet rs = ps.executeQuery();

        if (!rs.next()) {
            throw new BlueprintPersistenceException("Blueprint not found");
        }

        int blueprintId = rs.getInt("id");

        String queryPoints =
            "SELECT x, y FROM point WHERE blueprint_id=?";

        PreparedStatement psPoints =
            conn.prepareStatement(queryPoints);

        psPoints.setInt(1, blueprintId);

        ResultSet rsPoints = psPoints.executeQuery();

        List<Point> points = new ArrayList<>();

        while (rsPoints.next()) {
            points.add(new Point(
                rsPoints.getInt("x"),
                rsPoints.getInt("y")
            ));
        }

        return new Blueprint(author, name, points);

    } catch (SQLException e) {
        throw new BlueprintPersistenceException(
            "Error retrieving blueprint", e);
    }
}

```
- 'getBlueprintsByAuthor()':
    
```java
      @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author)
            throws BlueprintNotFoundException {

        Set<Blueprint> blueprints = new HashSet<>();

        try (Connection conn = getConnection()) {

            String query =
                    "SELECT name FROM blueprint WHERE author=?";

            PreparedStatement ps =
                    conn.prepareStatement(query);

            ps.setString(1, author);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String name = rs.getString("name");
                blueprints.add(getBlueprint(author, name));
            }

            return blueprints;

        } catch (SQLException e) {
            throw new BlueprintNotFoundException(
                    "Error retrieving blueprints by author: " + e.getMessage());
        }
    }
```

- 'getAllBlueprint()':

```java
@Override
    public Set<Blueprint> getAllBlueprints() {

        Set<Blueprint> blueprints = new HashSet<>();

        try (Connection conn = getConnection()) {

            String query =
                    "SELECT author, name FROM blueprint";

            PreparedStatement ps =
                    conn.prepareStatement(query);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String author = rs.getString("author");
                String name = rs.getString("name");
                try {
                    blueprints.add(getBlueprint(author, name));
                } catch (BlueprintNotFoundException e) {
                    // Skip blueprints that can't be found
                }
            }

            return blueprints;

        } catch (SQLException e) {
            // Return empty set on error
            return new HashSet<>();
        }
    }
```

- 'addPoint()':

```java
  @Override
    public void addPoint(String author, String name, int x, int y)
            throws BlueprintNotFoundException {

        try (Connection conn = getConnection()) {

            String queryBlueprint =
                    "SELECT id FROM blueprint WHERE author=? AND name=?";

            PreparedStatement ps =
                    conn.prepareStatement(queryBlueprint);

            ps.setString(1, author);
            ps.setString(2, name);

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new BlueprintNotFoundException("Blueprint not found");
            }

            int blueprintId = rs.getInt("id");

            String insertPoint =
                    "INSERT INTO point(blueprint_id, x, y) VALUES (?, ?, ?)";

            PreparedStatement psPoint =
                    conn.prepareStatement(insertPoint);

            psPoint.setInt(1, blueprintId);
            psPoint.setInt(2, x);
            psPoint.setInt(3, y);
            psPoint.executeUpdate();

        } catch (SQLException e) {
            throw new BlueprintNotFoundException(
                    "Error adding point: " + e.getMessage());
        }
    }
```
- Mantenemos el contrato de la interfaz `BlueprintPersistence`:

```java
import edu.eci.arsw.blueprints.model.Blueprint;
import java.util.Set;

public interface BlueprintPersistence {

    void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException;

    Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException;

    Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException;

    Set<Blueprint> getAllBlueprints();

    void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException;
}

```

### 3. Buenas prácticas de API REST

- Cambiamos el path base de los controladores a `/api/v1/blueprints`.  

```java
  @RestController
  @RequestMapping("/api/v1/blueprints")
  public class BlueprintsAPIController {}
```
- Usa **códigos HTTP** correctos:  
  
  - `200 OK` (consultas exitosas).  
  - `201 Created` (creación).  
  - `202 Accepted` (actualizaciones).  
  - `400 Bad Request` (datos inválidos).  
  - `404 Not Found` (recurso inexistente).  

Una vez aplicado sobre el codigo : 

```java
 // GET /blueprints
    @GetMapping
    public ResponseEntity<ApiResponse<Set<Blueprint>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Execute ok", services.getAllBlueprints()));
    }

    // GET /blueprints/{author}
    @GetMapping("/{author}")
    public ResponseEntity<?> byAuthor(@PathVariable String author) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(200, "Success", services.getBlueprintsByAuthor(author)));
        } catch (BlueprintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "Blueprint not found for author: " + author, null));
        }
    }

    // GET /blueprints/{author}/{bpname}
    @GetMapping("/{author}/{bpname}")
    public ResponseEntity<?> byAuthorAndName(@PathVariable String author, @PathVariable String bpname) {
        try {
            return ResponseEntity.ok(new ApiResponse<>(200, "Success", services.getBlueprint(author, bpname)));
        } catch (BlueprintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "Blueprint not found for author: " + author + " and name: " + bpname, null));
        }
    }

    // POST /blueprints
    @PostMapping
    public ResponseEntity<?> add(@Valid @RequestBody NewBlueprintRequest req) {
        try {
            Blueprint bp = new Blueprint(req.author(), req.name(), req.points());
            services.addNewBlueprint(bp);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Blueprint created", null));
        } catch (BlueprintPersistenceException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(400, "Invalid Data", null));
        }
    }

    // PUT /blueprints/{author}/{bpname}/points
    @PutMapping("/{author}/{bpname}/points")
    public ResponseEntity<?> addPoint(@PathVariable String author, @PathVariable String bpname,
                                      @RequestBody Point p) {
        try {
            services.addPoint(author, bpname, p.x(), p.y());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "Point added", null));
        } catch (BlueprintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "Invalid Data", null));
        }
    }
```
- Implementamos la clase genérica de respuesta uniforme:
  
```java
  package edu.eci.arsw.blueprints.dto;

  public record ApiResponse<T>(int code,String message,T data) {}
```

Ejemplo JSON:

  ```json
  {
    "code": 200,
    "message": "execute ok",
    "data": { "author": "john", "name": "house", "points": [...] }
  }
  ```

### 4. OpenAPI / Swagger

- Configuramos `springdoc-openapi` en el proyecto. 

- Se nos permite esto con la dependencia en el 'pom.xml' :

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```
- Para verificar que todo este bien ejecutamos : 

```bash
mvn clean install
```

Ejecutamos la aplicacion en local 
- Expón documentación automática en `/swagger-ui.html`.  
- Anota endpoints con `@Operation` y `@ApiResponse`.

### 5. Filtros de *Blueprints*
- Implementa filtros:
  - **RedundancyFilter**: elimina puntos duplicados consecutivos.  
  - **UndersamplingFilter**: conserva 1 de cada 2 puntos.  
- Activa los filtros mediante perfiles de Spring (`redundancy`, `undersampling`).  

---

## ✅ Entregables

1. Repositorio en GitHub con:  
   - Código fuente actualizado.  
   - Configuración PostgreSQL (`application.yml` o script SQL).  
   - Swagger/OpenAPI habilitado.  
   - Clase `ApiResponse<T>` implementada.  

2. Documentación:  
   - Informe de laboratorio con instrucciones claras.  
   - Evidencia de consultas en Swagger UI y evidencia de mensajes en la base de datos.  
   - Breve explicación de buenas prácticas aplicadas.  

---

## 📊 Criterios de evaluación

| Criterio | Peso |
|----------|------|
| Diseño de API (versionamiento, DTOs, ApiResponse) | 25% |
| Migración a PostgreSQL (repositorio y persistencia correcta) | 25% |
| Uso correcto de códigos HTTP y control de errores | 20% |
| Documentación con OpenAPI/Swagger + README | 15% |
| Pruebas básicas (unitarias o de integración) | 15% |

**Bonus**:  

- Imagen de contenedor (`spring-boot:build-image`).  
- Métricas con Actuator.  