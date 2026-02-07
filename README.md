
# Approach
This application uses Apache PDFBox (org.apache.pdfbox) to extract text content from FNOL PDF documents.
#### Why Apache PDFBox?

- Supports text-based PDFs like ACORD forms
- Provides multiple extraction strategies:
- Full-document text extraction
- Region-based (box) extraction
- AcroForm (fillable form) extraction
- Widely used, stable, and production-proven for document processing
### How PDFBox Is Used
When the PDF contains fillable AcroForm fields, the application extracts values directly using PDAcroForm.
This provides the most accurate and structured data.
``` bash
Map<String, String> formData = new HashMap<>();

try (PDDocument document = PDDocument.load(file.getInputStream())) {
    PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

    if (acroForm != null) {
        for (PDField field : acroForm.getFields()) {
            String fieldName = field.getFullyQualifiedName();
            String fieldValue = field.getValueAsString();

            if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                formData.put(fieldName, fieldValue.trim());
            }
        }
    }
}
```
### How Extracted Form Data Is Used
- fieldName → ACORD field identifier
(e.g., Text3, Check Box10)
- fieldValue → User-entered value
- Extracted values are stored in a
  Map<String, String> for downstream processing

### Advantages of This Approach

| Benefit | Description |
|-------|------------|
| Accuracy | Direct access to user-entered values |
| Stability | Independent of visual layout |
| Performance | Faster than regex-based scanning |
| Maintainability | Easy mapping to domain models |

# Assumptions & Clarifications

- **Effective Dates**
    - The ACORD Automobile Loss Notice form does not explicitly specify clear policy *effective start* and *end* dates.
    - Due to this ambiguity, the effective date is currently populated using the date provided at the top of the application form.
    - This assumption can be refined if a definitive policy period field is introduced.

- **Asset ID**
    - The form does not provide a dedicated Asset ID field.
    - For vehicle-related claims, the **license plate number** is used as the Asset ID.
    - This approach provides a consistent and uniquely identifiable reference for the asset.

- **Incomplete Driver Contact Information**
    - In the **OTHER VEHICLE / PROPERTY DAMAGED** section, the **Driver’s Name and Address** subsection includes a secondary phone field.
    - In the provided sample documents, this secondary phone field is not filled.
    - As a result, the secondary phone value is treated as optional and may be returned as `null`.

# Technologies Used
- **Spring Boot 3.2.0** - Application framework
- **Apache PDFBox 3.0.0** - PDF text extraction
- **Jackson** - JSON processing
- **Lombok** - Reduce boilerplate code
- **SpringDoc OpenAPI** - API documentation
- **Maven** - Build and dependency management
- **Docker** - 

# Steps To Run

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- Git
- Docker (Optional)

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/sujaysharvesh/fnol.git
cd fnol
```
### Add `application.properties`

Create `src/main/resources/application.properties` with the following content:

```properties
spring.application.name=fnol-agent
server.port=4001

springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.operationsSorter=method

spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```
2. **Build the project**
```bash
mvn clean install
```

3. **Run the application**
```bash
mvn spring-boot:run
```
the application will start on `http://localhost:8080`

# Troubleshooting
### Common Issues

**Issue**: Port 8080 already in use
```bash
# Change port in application.properties
server.port=8081
```

**Issue**: PDF extraction fails
```bash
# Ensure PDFBox dependency is present
mvn dependency:tree | grep pdfbox
```

**Issue**: Out of memory with large files
```bash
# Increase max file size in application.properties
spring.servlet.multipart.max-file-size=50MB
```

## **Optional if want run it with docker**
1. Build Docker Image
``` bash
docker build -t sujaysharvesh/fnol-agent:latest .
```
2. Start Docker Image
``` bash
docker stop $(docker ps -q --filter ancestor=sujaysharvesh/fnol-agent:latest)
```
3. Stop Docker Image
``` bash
docker run -d -p 8080:8080 sujaysharvesh/fnol-agent:latest
```

### Quick Test

```bash
# Health check
curl http://localhost:8080/api/v1/fnol/health

# Process a sample document
curl -X POST http://localhost:8080/api/v1/fnol/process \
  -F "file=@sample-documents/sample1.pdf"
```

# API Documentation

Once the application is running, access the interactive API documentation:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs
### Main Endpoint

**POST** `/api/v1/fnol/process`

Upload and process a FNOL document.

**Request:**
- Content-Type: `multipart/form-data`
- Parameter: `file` (PDF or TXT file)